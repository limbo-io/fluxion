# Fluxion 测试指南

本文档介绍 Fluxion 任务调度平台的测试流程与真实测试清单。所有 profile、测试类与命令均可用仓库验证。

> 术语以 [CONTEXT.md](../../CONTEXT.md) 为权威。测试围绕当前 Execution 调度模型组织；旧文档中的 `ScheduleLeaseMySqlTest`、`ExecutionRecoveryMySqlTest`、`BrokerMultiNodeLeaseTest`、`LeaseBoundaryTest` 已在 `ScheduleDelay → Execution` 合并中删除，不再存在。

## 分类与新增测试的固定路径

所有路径均相对于 `fluxion-test/src/test/java/`，Java package 必须与目录一致。

| 类型 | 固定路径 | 放置标准 | 执行入口 |
| --- | --- | --- | --- |
| 单元测试 | `io/fluxion/test/unit/<业务模块>/` | 单类或少量对象协作，可使用 mock；不启动 Spring 上下文、真实数据库或节点进程 | 默认 `mvn test -pl fluxion-test -am`，或 `-Pregression-test` |
| 数据库/组件集成测试 | `io/fluxion/test/integration/database/` | Spring 装配、持久化、事务、唯一约束、并发领取和租约 | `-Ph2-integration-test`；MySQL 专属语义使用 `-Pmysql-integration-test` |
| 集群端到端测试 | `io/fluxion/test/integration/cluster/` | 真实 API → 调度 → Worker 执行 → 上报 → 终态，含节点故障恢复 | `-Pcluster-e2e`，需要 Podman |
| 跨类别共用装配 | `io/fluxion/test/support/` | 无独立测试入口的共用配置和工具 | 随使用方运行 |
| 单类别专用工具 | 所属类别下的 `support/` 或 `node/` | 例如集群生命周期、事件接收器、测试节点启动器 | 随场景运行 |

`unit/common/` 存放通用工具测试，`unit/json/` 等目录存放无需服务启动的协议测试。需要真实服务交互的协议兼容验证归入集成测试；不另外建立顶层协议目录。

新增测试统一命名为 `*Test.java`（单元测试入口也兼容 `*Tests.java`）。集群场景沿用稳定编号，例如 `Sch001Test`、`Wrk001Test`；单个类可包含同一场景的多个验证步骤。新增 MySQL 专属测试时，必须同时加入 `mysql-integration-test` 的 include 和 `h2-integration-test` 的 exclude，避免误跑或漏跑。其他数据库集成测试默认由 H2 profile 发现。

测试分类按实际依赖决定，不按被测类名决定。数据库锁的异常重试可以通过 mock 做单元测试；数据库锁的互斥与事务语义必须做集成测试。现有 `core/` 已迁入 `unit/`，原 `integration/mysql/` 已迁入 `integration/database/`，原 `cluster/` 已迁入 `integration/cluster/`。

**例外**：`io/fluxion/server/core/execution/service/ExecutionScheduleClaimBridge.java` 是测试源码中的同包访问桥，必须与生产类同 package 才能访问 protected 方法，保留在原路径。它不属于生产代码，也不是独立测试用例。

冒烟、回归、正常链路与故障恢复是运行集合或场景属性，不另外复制测试到新目录。当前未提供冒烟集合与并行编排器；单类或单场景可通过 Maven `-Dtest` 选择，例如：

```bash
# 单元测试类
mvn test -pl fluxion-test -am -Dtest=WorkflowConfigValidationTest -Dsurefire.failIfNoSpecifiedTests=false
# 数据库集成测试类
mvn test -pl fluxion-test -am -Ph2-integration-test -Dtest=MultiBrokerClaimConcurrencyTest -Dsurefire.failIfNoSpecifiedTests=false
# 集群场景
mvn test -pl fluxion-test -am -Pcluster-e2e -Dtest=Wrk001Test -Dsurefire.failIfNoSpecifiedTests=false
```

迁移目录后，首次执行前运行 `mvn clean -pl fluxion-test` 清除旧 package 的 class 文件。不要同时启用多个测试 profile；目录类别调整不新增外部服务或 CI 门禁。

## 集群端到端回归设计

当前已实现 SCH-001 和 WRK-001 的真实集群运行入口和基础报告，WRK-001 覆盖首个 Worker 强退恢复闭环。Broker 接管、迟到结果隔离、其余业务场景、场景组选择和并行模式仍待实现。完整设计见 [总体方案](CLUSTER_E2E_TESTING.md)、[用例目录](CLUSTER_E2E_CASES.md) 和 [实现与验收约定](CLUSTER_E2E_DELIVERY.md)。

## 快速开始

```bash
# 1. 回归测试（排除 integration/database 包，H2 驱动）
mvn test -pl fluxion-test -am -Pregression-test

# 2. 集成回归（只跑 integration/database 包；H2 MySQL 兼容模式）
mvn test -pl fluxion-test -am -Ph2-integration-test

# 3. 真实集群回归（需运行中的 Podman；SCH-001 与 WRK-001）
mvn test -pl fluxion-test -am -Pcluster-e2e

# 仅验证 Worker 强退恢复链路（不增加 CI 门禁）
mvn test -pl fluxion-test -am -Pcluster-e2e -Dtest=Wrk001Test -Dsurefire.failIfNoSpecifiedTests=false
```

> 注意：JDK 要求 21（`.mvn` 与 pom 的 release 配置）。本地若只有 JDK 17，测试不会执行。

## 回归测试（regression-test）

只跑 `**/unit/**/*Test.java` / `**/unit/**/*Tests.java`，不会启动集成测试。使用 `-am` 确保测试运行的是本次构建出的 server/worker 模块，而不是本地 Maven 仓库的旧依赖。

```bash
mvn test -pl fluxion-test -am -Pregression-test
```

覆盖调度计算与调度器、恢复配置校验、Broker 生命周期、Job 重试命令、时间协议、Workflow 校验、Worker 筛选及 Deadline 等组件行为。旧禁用空壳 ExecutorExecutionTest 已删除；其首批 Job 创建行为由 FirstBatchJobTriggerAtTest 中的有效用例覆盖，完整执行链路由集群场景验证。

## 集成回归（h2-integration-test）

**只跑** `fluxion-test/src/test/java/io/fluxion/test/integration/database/` 下的测试，用 H2（MySQL 兼容模式）作为数据库：

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

## 真实集群回归（cluster-e2e）

从仓库根目录执行。准备 JDK 21、PATH 中可用的 `mvn` 和 `podman`，并确认 Podman machine 已启动；首次运行需要拉取 `docker.io/library/mysql:8.4` 镜像。

```bash
# 检查运行时；若 machine 未启动，先执行 podman machine start
java -version
mvn -version
podman info

# 独立启动并验证真实集群
mvn test -pl fluxion-test -am -Pcluster-e2e
```

该入口会嵌套构建当前代码的 Broker 可执行 jar，自动启动专用 MySQL、2 个 Broker 与 2 个 Worker，再通过 API 创建、发布、启用 FIXED_RATE + STANDALONE 任务。SCH-001 选择两个连续计划触发点，检查 Job 派发、Worker 业务事件、结果上报、配置版本和终态，最后禁用任务并清理资源。

不需要手动启动节点、创建任务或配置外部数据库。默认参数下包含真实扫描等待，通常需要数分钟；预生成等待预算为 20 分钟，不能用单元测试的耗时估计它。另有 `Wrk001Test`：首次业务开始后阻塞，根据事件精准强杀对应 Worker，等待自然失联检测和存活 Worker 重试，检查原 Job 身份、新 dispatch attempt、一次业务重试和 Execution 成功终态。它是首个本地恢复闭环，不代表 Broker 故障或迟到结果隔离已验证。暂未提供按场景 ID/组筛选、快速参数和并行运行入口，不要自行套用设计中的选项。

嵌套构建会对 Broker 及其上游模块执行 `clean package -DskipTests`，同一工作目录不要同时进行另一次构建或集群测试。不同 profile 分别运行，不将多套 profile 合并为一次命令。

### 结果与产物

每次运行写入独立目录，相对于仓库根目录：

```text
fluxion-test/target/cluster-e2e/<run-id>/
  results.json                    # 权威结果：状态、失败阶段、断言及错误
  report.html                     # 离线明细及产物链接
  junit.xml                       # 该次运行的 JUnit 派生报告
  manifest.json                   # 代码版本、dirty 标记、节点身份
  nodes/                          # 节点日志和 Worker 本地存储
  cases/SCH-001/default/
    events.jsonl                  # 实际业务开始/完成事件
    assertions.json               # 每项预期、实际值和结论
    snapshots/                    # 本次已采集的数据库快照
```

以终局验证的 run-id `20261016T102619Z-SCH-001` 为例，可直接打开该目录的 `report.html`。通过要求同时满足 Maven 成功退出、`results.json.status=PASS` 且无失败断言；当前样板正常运行有 38 项断言。Maven 自身的测试结果另见 `fluxion-test/target/surefire-reports/`。

HTML/JUnit 写入失败时，已写出的派生报告可能保留之前状态；`results.json` 会记录最终运行级错误，构建返回失败。CI 不可仅根据派生 `junit.xml` 的失败计数放行；若权威 JSON 缺失，也不能视为成功。

### 失败排查与当前边界

1. 先读 `results.json` 的 `status`、`failureStage`、`errorMessage` 和失败断言，区分环境、触发、业务或报告错误。
2. 查对应节点日志及 `events.jsonl`；已采集的快照在 `snapshots/`。失败发生得较早时可能尚未生成终态快照，不把文件缺失当成空数据库证据。
3. 初始化清理失败时，Maven 异常包含 suppressed；控制台会输出 `mysql container may have leaked` 及容器名。用 `podman ps -a` 核对，仅清理该次运行资源。

产物保留在 target 下，再次 `mvn clean` 前应复制需要跟进的运行目录。当前 Worker 按顺序注册，规避了已知的并发注册死锁；SCH-001 通过不代表该问题已修复。运行期故障的线程栈及更完整快照采集属于后续批次。

## 可用 Maven Profiles

仓库仅存在以下测试 profile（见 `fluxion-test/pom.xml`）：

| Profile | 命令 | 说明 |
|---------|------|------|
| `regression-test` | `mvn test -pl fluxion-test -am -Pregression-test` | 跑组件等测试，排除 `integration/database` 和 `cluster` 包 |
| `h2-integration-test` | `mvn test -pl fluxion-test -am -Ph2-integration-test` | 只跑 `integration/database` 包（H2 MySQL 兼容模式），排除锁及身份 upsert 两个原生 MySQL 类 |
| `mysql-integration-test` | `mvn test -pl fluxion-test -am -Pmysql-integration-test` | 运行 `DistributedLockMySqlTest`、`ExecutionIdentityUpsertMySqlTest`，需要真实 MySQL |
| `cluster-e2e` | `mvn test -pl fluxion-test -am -Pcluster-e2e` | 自动启动真实集群，运行 `Sch001Test` 与 `Wrk001Test`；需要 Podman |

## 真实测试清单

### unit/（单元测试，regression-test）

| 测试类 | 覆盖场景 |
|--------|----------|
| `unit/broker/BrokerLifecycleTest` | Broker 启动时注册全部 core task（ScheduleLoader、ExecutionLoader、各 Checker 等） |
| `unit/job/JobRetryCommandServiceTest` | 重试触发时 Job 状态重置并可重新运行 |
| `unit/schedule/ScheduleRecoveryOptionTest` | 遗漏触发恢复配置边界；历史点处理以 CONTEXT 的有界恢复语义为准 |
| `unit/schedule/DelayedTaskSchedulerTest` | 延迟任务入轮/到点执行/重复抑制/停止/过期触发时间 |
| `unit/schedule/PeriodicTaskSchedulerTest` | 固定速率、固定延迟、停止、时间窗口外调度 |
| `unit/worker/WorkerCpuLoadSelectionTest` | LEAST_CPU_LOAD 选择最低负载 Worker、CPU 阈值过滤 |
| `unit/worker/WorkerLoadBalancingTest` | 按 executor/CPU/内存过滤、RANDOM/ROUND_ROBIN 负载均衡 |
| `unit/worker/WorkerDispatchDeadlineTest` | Worker 派发 Deadline 校验 |
| `unit/json/TimeWireProtocolTest` | 运行态时间的跨时区协议 |
| `unit/lock/DatabaseDistributedLockDeadlockRetryTest` | 数据库锁死锁重试 |
| `unit/workflow/FirstBatchJobTriggerAtTest` | 首批 Job 的 triggerAt 传递 |
| `unit/workflow/WorkflowConfigValidationTest` | Workflow 配置校验 |

### integration/database/（真实 MySQL 语义，H2 兼容模式跑）

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

`JsonTest` 和 `ReflectionTest` 位于 `io/fluxion/test/unit/common/`，验证 JSON 字段、多态配置以及 RPC/CQRS 泛型响应类型；历史文档中的 `BacklogStrategyTest`、`WorkerOfflineMigrationTest` 当前不在测试源码清单中，不应据此宣称覆盖。

### support/（测试基础设施）

| 类 | 作用 |
|----|------|
| `support/base/TestApplication` | 共享 Spring 测试装配（非单元测试入口）（注意：不存在 `BaseIntegrationTest`） |
| `support/environment/LocalDistributedLock` | 本地锁实现，替代数据库分布式锁 |
| `integration/database/MySqlTestApplication` | integration/database 包测试的 Spring 入口 |
| `integration/database/AbstractMySqlIntegrationTest` | 空 abstract 基类（仅作类型标记） |

## 编写新测试

### 1. 单元测试（unit/）

```java
package io.fluxion.test.unit.schedule;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MyComponentTest {
    @Test
    void shouldDoSomething() {
        // Given / When / Then
    }
}
```

### 2. MySQL 语义测试（integration/database/）

```java
package io.fluxion.test.integration.database;

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
| 空目录遗迹 | `unit/execution`、`unit/faulttolerance/{failover,retry,store,timeout}`、`unit/job/fault` | 空目录，无测试，可清理 |
| 测试支撑 | `io.fluxion.server.core.execution.service.ExecutionScheduleClaimBridge` | 同包桥（测试源码），暴露 protected `claim()` 供并发测试直击原子原语，非测试类不参与任何 profile |

## 注意事项

1. `DistributedLockMySqlTest` 和 `ExecutionIdentityUpsertMySqlTest` 使用现有 `mysql-integration-test` profile 在真实 MySQL 上运行。
2. 当前 CI 中只执行 `regression-test` 与 `h2-integration-test` 两个 profile。
3. 测试文件命名遵循 `*Test.java` 或 `*Tests.java` 模式。
4. H2 回归不替代 MySQL 原生语义验证；原生测试入口已存在，但当前两个 CI workflow 未运行该 profile。本文整理时未重新执行这些测试，profile 存在不等于本次验证通过，更不等于集群端到端已覆盖。
