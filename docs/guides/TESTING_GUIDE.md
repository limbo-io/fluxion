# Fluxion 测试指南

本文档介绍 Fluxion 任务调度平台的测试流程与真实测试清单。所有 profile、测试类与命令均可用仓库验证。

> 术语以 [CONTEXT.md](../../CONTEXT.md) 为权威。测试围绕当前 Execution 调度模型组织；旧文档中的 `ScheduleLeaseMySqlTest`、`ExecutionRecoveryMySqlTest`、`BrokerMultiNodeLeaseTest`、`LeaseBoundaryTest` 已在 `ScheduleDelay → Execution` 合并中删除，不再存在。

## 快速开始

```bash
# 1. 回归测试（排除 integration/mysql 包，H2 驱动）
mvn test -pl fluxion-test -am -Pregression-test

# 2. 集成回归（只跑 integration/mysql 包；H2 MySQL 兼容模式）
mvn test -pl fluxion-test -am -Ph2-integration-test
```

> 注意：JDK 要求 21（`.mvn` 与 pom 的 release 配置）。本地若只有 JDK 17，测试不会执行。

## 回归测试（regression-test）

跑 `**/*Test.java` / `**/*Tests.java`，**排除** `**/integration/mysql/**`。使用 `-am` 确保测试运行的是本次构建出的 server/worker 模块，而不是本地 Maven 仓库的旧依赖。

```bash
mvn test -pl fluxion-test -am -Pregression-test
```

覆盖：调度配置（固定速率/延迟/Cron）、积压策略、时间轮调度器、Broker 生命周期、Executor/Job 协作、Worker 过滤与负载均衡。

## 集成回归（h2-integration-test）

**只跑** `fluxion-test/src/test/java/io/fluxion/test/integration/mysql/` 下的测试，用 H2（MySQL 兼容模式）作为数据库：

```bash
mvn test -pl fluxion-test -am -Ph2-integration-test
```

覆盖 Execution 唯一身份约束、Job 容错字段持久化、Job 租约与 attempt fencing、Worker 下线迁移链路与分布式锁语义。

### H2 的边界

`h2-integration-test` 明确排除 `DistributedLockMySqlTest`：MySQL 原子 upsert（`INSERT ... ON DUPLICATE KEY`）语义不能由 H2 表达。**分布式锁的 MySQL 行为当前无自动化入口**：

- 仓库**没有** `mysql-integration-test` profile（旧文档介绍的 Testcontainers 命令不存在）。
- 该类需真实 MySQL 时，可参考 `fluxion-test/src/test/resources/application-test-mysql.yml` 自行配置外部 MySQL（`FLUXION_TEST_MYSQL_*` 环境变量），但当前没有现成的 Maven 命令把它跑起来——这是待补的运维缺口。

## 可用 Maven Profiles

仓库仅存在以下两个测试 profile（见 `fluxion-test/pom.xml`）：

| Profile | 命令 | 说明 |
|---------|------|------|
| `regression-test` | `mvn test -pl fluxion-test -am -Pregression-test` | 跑所有测试，排除 `integration/mysql` 包 |
| `h2-integration-test` | `mvn test -pl fluxion-test -am -Ph2-integration-test` | 只跑 `integration/mysql` 包（H2），排除 `DistributedLockMySqlTest` |

不存在 `mysql-integration-test` profile。CI 使用的就是以上两个（见下）。

## 真实测试清单

### core/（组件回归，regression-test）

| 测试类 | 覆盖场景 |
|--------|----------|
| `core/broker/BrokerLifecycleTest` | Broker 启动时注册全部 core task（ScheduleLoader、ExecutionLoader、各 Checker 等） |
| `core/executor/ExecutorExecutionTest` | Executor 为当前 Execution 创建 Job |
| `core/job/JobRetryCommandServiceTest` | 重试触发时 Job 状态重置并可重新运行 |
| `core/schedule/BacklogStrategyTest` | 积压策略保留**全部**历史与未来触发点（不折叠、不丢弃——与 CONTEXT“历史补建不折叠”一致） |
| `core/schedule/DelayedTaskSchedulerTest` | 延迟任务入轮/到点执行/重复抑制/停止/过期触发时间 |
| `core/schedule/PeriodicTaskSchedulerTest` | 固定速率、固定延迟、停止、时间窗口外调度 |
| `core/worker/WorkerCpuLoadSelectionTest` | LEAST_CPU_LOAD 选择最低负载 Worker、CPU 阈值过滤 |
| `core/worker/WorkerLoadBalancingTest` | 按 executor/CPU/内存过滤、RANDOM/ROUND_ROBIN 负载均衡 |

### integration/mysql/（真实 MySQL 语义，H2 兼容模式跑）

| 测试类 | 覆盖场景 |
|--------|----------|
| `ExecutionIdentityConstraintTest` | `(triggerId, triggerAt)` 唯一约束拒绝重复创建 Execution（ScheduleDelay→Execution 合并的核心回归） |
| `JobFaultStatePersistenceTest` | `dispatch_attempt`/`lease_owner`/`lease_until`/`timeout_at`/`next_retry_at` 容错字段持久化 |
| `JobLeaseServiceTest` | 租约 owner 单调分配 attempt、活跃 Job 续租、旧 attempt 结果拒绝、RETRY_WAIT 到期与唯一性 |
| `WorkerOfflineMigrationTest` | Worker 指派检索、`recovery_owner` 与 worker 分离、终态排除，验证 Job 级接管 |
| `ObservationOverviewMySqlTest` | 存量观测接口：状态分布 zero-fill、积压/misfire 候选/可回收租约边界（见 [operations.md](../guides/operations.md)「可观测性现状」） |
| `MultiBrokerClaimConcurrencyTest` | 多 Broker 并发：双线程条件 UPDATE claim 竞争仅一胜者、并发创建唯一键兜底、租约过期bucket 移交后 handle() 完整接管（真实多线程） |
| `DistributedLockMySqlTest` | MySQL 原子锁：获取/失败/过期/超时并发/仅持有者解锁（**被 h2 profile 排除**，见上文） |
| `JsonTest` / `ReflectionTest` | 库级工具回归（非业务） |

### support/（测试基础设施）

| 类 | 作用 |
|----|------|
| `support/base/TestApplication` | core 组件测试的 Spring 入口（注意：不存在 `BaseIntegrationTest`） |
| `support/environment/LocalDistributedLock` | 本地锁实现，替代数据库分布式锁 |
| `integration/mysql/MySqlTestApplication` | integration/mysql 包测试的 Spring 入口 |
| `integration/mysql/AbstractMySqlIntegrationTest` | 空 abstract 基类（仅作类型标记） |

## 编写新测试

### 1. 组件测试（core/）

```java
package io.fluxion.test.core.schedule;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MyComponentTest {
    @Test
    void shouldDoSomething() {
        // Given / When / Then
    }
}
```

### 2. MySQL 语义测试（integration/mysql/）

```java
package io.fluxion.test.integration.mysql;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = MySqlTestApplication.class)
@ActiveProfiles("test-mysql")
class MyMySqlTest {

    @Test
    void shouldWorkWithMySQL() {
        // 事务/条件更新/锁语义验证
    }
}
```

配置见 `fluxion-test/src/test/resources/application-test-mysql.yml` 与 `schema-mysql.sql`。

## CI 流水线（.github/workflows/）

### `test.yml`

```
Push/PR → mvn test → mvn test -Pregression-test → mvn test -Ph2-integration-test
```

### `ci.yml`

```
Push/PR → mvn test -Pregression-test → mvn test -Ph2-integration-test
```

```bash
# 本地模拟 CI
mvn clean
mvn test -pl fluxion-test -am --batch-mode                    # 默认单元测试
mvn test -pl fluxion-test -am -Pregression-test --batch-mode  # 回归测试
mvn test -pl fluxion-test -am -Ph2-integration-test --batch-mode  # 集成回归
```

## 历史清理记录（供考古）

| 时代 | 测试类 | 现状 |
|------|--------|------|
| ScheduleDelay 时期 | `FencingConditionMySqlTest`、`ExecutionRegistrationTest`、`ExecutionResultTest`、`ExecutionStateTest`、`ErrorCategoryTest` | 已删除（纯 DTO/enum/临时表测试，无业务覆盖） |
| ScheduleDelay 时期 | `ScheduleLeaseMySqlTest`、`ExecutionRecoveryMySqlTest`、`BrokerMultiNodeLeaseTest`、`LeaseBoundaryTest` | 在 `ScheduleDelay → Execution` 合并中删除 |
| ScheduleDelay 时期 | `ScheduleTaskTest` | 已重写为 `PeriodicTaskSchedulerTest`/`DelayedTaskSchedulerTest` 等 |
| 空目录遗迹 | `core/execution`、`core/faulttolerance/{failover,retry,store,timeout}`、`core/job/fault` | 空目录，无测试，可清理 |
| 测试支撑 | `io.fluxion.server.core.execution.service.ExecutionScheduleClaimBridge` | 同包桥（测试源码），暴露 protected `claim()` 供并发测试直击原子原语，非测试类不参与任何 profile |

## 注意事项

1. `DistributedLockMySqlTest` 无法在 H2 下运行，只能跑在真实 MySQL 上（当前无现成 profile，见上文）。
2. 当前 CI 中只执行 `regression-test` 与 `h2-integration-test` 两个 profile。
3. 测试文件命名遵循 `*Test.java` 或 `*Tests.java` 模式。
4. H2 回归不覆盖 MySQL 原生 SQL 语义（原子 upsert、锁竞争、隔离级别），生产 MySQL 验证仍是未闭环项。