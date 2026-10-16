# Fluxion 测试指南

本文档介绍 Fluxion 任务调度平台的测试流程与真实测试清单。所有 profile、测试类与命令均可用仓库验证。

> 术语以 [CONTEXT.md](../../CONTEXT.md) 为权威。测试围绕当前 Execution 调度模型组织；旧文档中的 `ScheduleLeaseMySqlTest`、`ExecutionRecoveryMySqlTest`、`BrokerMultiNodeLeaseTest`、`LeaseBoundaryTest` 已在 `ScheduleDelay → Execution` 合并中删除，不再存在。

## 集群端到端回归设计

针对真实定时触发、独立 Broker/Worker、节点强制退出与恢复的测试设计，见 [总体方案](CLUSTER_E2E_TESTING.md)、[完整用例目录](CLUSTER_E2E_CASES.md) 和 [实现与验收约定](CLUSTER_E2E_DELIVERY.md)。设计使用 Podman MySQL、JUnit 场景选择、串行/并行隔离及 HTML/JSON/JUnit 报告；尚未实现，本文现有 profile 不代表已运行这些集群场景。

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

覆盖调度计算与调度器、恢复配置校验、Broker 生命周期、Job 重试命令、时间协议、Workflow 校验、Worker 筛选及 Deadline 等组件行为。存在的测试类不等于实际启用：ExecutorExecutionTest 当前为 @Disabled，不能作为完整执行链路已覆盖的证据。

## 集成回归（h2-integration-test）

**只跑** `fluxion-test/src/test/java/io/fluxion/test/integration/mysql/` 下的测试，用 H2（MySQL 兼容模式）作为数据库：

```bash
mvn test -pl fluxion-test -am -Ph2-integration-test
```

覆盖 Execution 唯一身份约束、Job 容错字段持久化、Job 租约与 attempt fencing、调度反馈、遗漏触发恢复和所有权等业务/事务行为；不启动独立 Worker 集群验证真实故障接管。

### H2 的边界

`h2-integration-test` 明确排除 `DistributedLockMySqlTest` 和 `ExecutionIdentityUpsertMySqlTest`：MySQL 原子 upsert（`INSERT ... ON DUPLICATE KEY`）的真实驱动及锁语义需在 MySQL 验证。真实 MySQL 回归入口：

```bash
# 需可用 MySQL，默认 root/空密码 @127.0.0.1:3306/fluxion_test，可用环境变量覆盖
export FLUXION_TEST_MYSQL_PASSWORD=***
mvn test -pl fluxion-test -am -Pmysql-integration-test
```

连接配置见 `fluxion-test/src/test/resources/application-test-real-mysql.yml`（`FLUXION_TEST_MYSQL_*` 环境变量可覆盖 host/port/db/user/password）。MySQL 不可用时该 profile 直接失败，不静默跳过。

## 可用 Maven Profiles

仓库仅存在以下测试 profile（见 `fluxion-test/pom.xml`）：

| Profile | 命令 | 说明 |
|---------|------|------|
| `regression-test` | `mvn test -pl fluxion-test -am -Pregression-test` | 跑所有测试，排除 `integration/mysql` 包 |
| `h2-integration-test` | `mvn test -pl fluxion-test -am -Ph2-integration-test` | 只跑 `integration/mysql` 包（H2 MySQL 兼容模式），排除锁及身份 upsert 两个原生 MySQL 类 |
| `mysql-integration-test` | `mvn test -pl fluxion-test -am -Pmysql-integration-test` | 运行 `DistributedLockMySqlTest`、`ExecutionIdentityUpsertMySqlTest`，需要真实 MySQL |

## 真实测试清单

### core/（组件回归，regression-test）

| 测试类 | 覆盖场景 |
|--------|----------|
| `core/broker/BrokerLifecycleTest` | Broker 启动时注册全部 core task（ScheduleLoader、ExecutionLoader、各 Checker 等） |
| `core/executor/ExecutorExecutionTest` | 当前 @Disabled；不计为有效覆盖 |
| `core/job/JobRetryCommandServiceTest` | 重试触发时 Job 状态重置并可重新运行 |
| `core/schedule/ScheduleRecoveryOptionTest` | 遗漏触发恢复配置边界；历史点处理以 CONTEXT 的有界恢复语义为准 |
| `core/schedule/DelayedTaskSchedulerTest` | 延迟任务入轮/到点执行/重复抑制/停止/过期触发时间 |
| `core/schedule/PeriodicTaskSchedulerTest` | 固定速率、固定延迟、停止、时间窗口外调度 |
| `core/worker/WorkerCpuLoadSelectionTest` | LEAST_CPU_LOAD 选择最低负载 Worker、CPU 阈值过滤 |
| `core/worker/WorkerLoadBalancingTest` | 按 executor/CPU/内存过滤、RANDOM/ROUND_ROBIN 负载均衡 |
| `core/worker/WorkerDispatchDeadlineTest` | Worker 派发 Deadline 校验 |
| `core/json/TimeWireProtocolTest` | 运行态时间的跨时区协议 |
| `core/lock/DatabaseDistributedLockDeadlockRetryTest` | 数据库锁死锁重试 |
| `core/workflow/FirstBatchJobTriggerAtTest` | 首批 Job 的 triggerAt 传递 |
| `core/workflow/WorkflowConfigValidationTest` | Workflow 配置校验 |

### integration/mysql/（真实 MySQL 语义，H2 兼容模式跑）

| 测试类 | 覆盖场景 |
|--------|----------|
| `ExecutionIdentityConstraintTest` | `(triggerId, triggerAt)` 唯一约束拒绝重复创建 Execution（ScheduleDelay→Execution 合并的核心回归） |
| `JobFaultStatePersistenceTest` | `dispatch_attempt`/`lease_owner`/`lease_until`/`timeout_at`/`next_retry_at` 容错字段持久化 |
| `JobLeaseServiceTest` | 租约 owner 单调分配 attempt、活跃 Job 续租、旧 attempt 结果拒绝、RETRY_WAIT 到期与唯一性 |
| `ObservationOverviewMySqlTest` | 存量观测接口：状态分布 zero-fill、积压/misfire 候选/可回收租约边界（见 [operations.md](../guides/operations.md)「可观测性现状」） |
| `MultiBrokerClaimConcurrencyTest` | 多 Broker 并发：双线程条件 UPDATE claim 竞争仅一胜者、并发创建唯一键兜底、租约过期bucket 移交后 handle() 完整接管（真实多线程） |
| `DistributedLockMySqlTest` | MySQL 原子锁：获取/失败/过期/超时并发/仅持有者解锁（**被 h2 profile 排除**，见上文） |
| `ExecutionIdentityUpsertMySqlTest` | MySQL Execution 身份 upsert、并发、驱动及会话语义（被 h2 profile 排除） |
| `FixedDelayScheduleFeedbackTest` | FIXED_DELAY 终态反馈推进 |
| `JobDeadlineExpireTest` | Job 激活过期收敛 |
| `MissedTriggerRecoveryTest` | 未物化历史触发点的有界恢复 |
| `ScheduleOwnershipTest` | Bucket Owner 与 Schedule 游标处理 |
| `UtcRuntimeTimezoneTest` | 运行态 UTC 持久化及时间比较 |

`JsonTest` 和 `ReflectionTest` 位于 `io/fluxion/test/` 根包，为库级工具回归；历史文档中的 `BacklogStrategyTest`、`WorkerOfflineMigrationTest` 当前不在测试源码清单中，不应据此宣称覆盖。

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

配置见 `fluxion-test/src/test/resources/application-test-mysql.yml`，schema 由 Flyway `migration/` 维护。

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

1. `DistributedLockMySqlTest` 和 `ExecutionIdentityUpsertMySqlTest` 使用现有 `mysql-integration-test` profile 在真实 MySQL 上运行。
2. 当前 CI 中只执行 `regression-test` 与 `h2-integration-test` 两个 profile。
3. 测试文件命名遵循 `*Test.java` 或 `*Tests.java` 模式。
4. H2 回归不替代 MySQL 原生语义验证；原生测试入口已存在，但当前两个 CI workflow 未运行该 profile。本文整理时未重新执行这些测试，profile 存在不等于本次验证通过，更不等于集群端到端已覆盖。
