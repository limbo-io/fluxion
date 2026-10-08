# Fluxion Test Module

集中存放所有层级的测试代码。清单与目录均以仓库实况为准。

## 📁 模块结构

```
fluxion-test/src/test/java/io/fluxion/test/
│
├── support/                          # 测试基础设施
│   ├── base/
│   │   └── TestApplication.java      #   core 组件测试 Spring 入口
│   └── environment/
│       └── LocalDistributedLock.java #   本地锁（替代数据库分布式锁）
│
├── core/                             # 组件回归（regression-test）
│   ├── broker/
│   │   └── BrokerLifecycleTest.java
│   ├── executor/
│   │   └── ExecutorExecutionTest.java
│   ├── job/
│   │   └── JobRetryCommandServiceTest.java
│   ├── json/
│   │   └── TimeWireProtocolTest.java               # 时间协议跨时区回归
│   ├── lock/
│   │   └── DatabaseDistributedLockDeadlockRetryTest.java  # 锁死锁自愈契约
│   ├── schedule/
│   │   ├── ScheduleRecoveryOptionTest.java       # recoveryEnabled 配置边界
│   │   ├── DelayedTaskSchedulerTest.java
│   │   └── PeriodicTaskSchedulerTest.java
│   ├── workflow/
│   │   ├── FirstBatchJobTriggerAtTest.java        # 首批 Job triggerAt 透传
│   │   └── WorkflowConfigValidationTest.java
│   └── worker/
│       ├── WorkerCpuLoadSelectionTest.java
│       ├── WorkerDispatchDeadlineTest.java
│       └── WorkerLoadBalancingTest.java
│
├── integration/                      # MySQL 语义集成测试
│   └── mysql/
│       ├── MySqlTestApplication.java                # Spring 入口（真实 MySQL 专用锁/驱动 Bean）
│       ├── AbstractMySqlIntegrationTest.java          # 空 abstract 基类（类型标记）
│       ├── ExecutionIdentityConstraintTest.java     # (triggerId,triggerAt) 唯一约束（h2）
│       ├── ExecutionIdentityUpsertMySqlTest.java    # 身份 upsert 谓词/行数/并发竞争（仅真实 MySQL）
│       ├── FixedDelayScheduleFeedbackTest.java      # FIXED_DELAY 反馈推进
│       ├── JobDeadlineExpireTest.java               # Job 激活截止超时
│       ├── JobFaultStatePersistenceTest.java        # Job 容错字段持久化
│       ├── JobLeaseServiceTest.java                 # Job 租约/fencing/RETRY_WAIT
│       ├── MissedTriggerRecoveryTest.java           # 有界遗漏触发恢复（R1–R13）
│       ├── MultiBrokerClaimConcurrencyTest.java    # 多 Broker 领取并发/幂等
│       ├── ObservationOverviewMySqlTest.java        # 观测指标聚合
│       ├── ScheduleOwnershipTest.java               # Bucket Owner 游标收敛
│       ├── UtcRuntimeTimezoneTest.java              # 运行态 UTC 时间模型回归
│       ├── WorkerOfflineMigrationTest.java         # Worker 下线 Job 级接管
│       └── DistributedLockMySqlTest.java            # MySQL 原子锁（仅真实 MySQL）
│
└── JsonTest.java / ReflectionTest.java          # 库级工具回归
```

> 历史空目录 `core/execution`、`core/faulttolerance/{failover,retry,store,timeout}`、`core/job/fault` 下没有任何测试文件。

---

## 🧪 测试分层

| 层级 | 目录 | 特点 | 执行 profile |
|------|------|------|------------|
| **组件回归** | `core/` | H2、Spring 上下文 | `regression-test` |
| **MySQL 语义集成（H2 门禁）** | `integration/mysql/` | H2 MySQL 兼容模式；业务/事务语义 | `h2-integration-test`（排除 `DistributedLockMySqlTest`、`ExecutionIdentityUpsertMySqlTest`） |
| **真实 MySQL 集成** | `integration/mysql/` | 真实 InnoDB/驱动方言（upsert 锁、身份毫等写入、会话时区） | `mysql-integration-test` |

---

## 🚀 如何运行

```bash
# 回归测试（排除 integration/mysql 包）
mvn test -pl fluxion-test -am -Pregression-test

# 集成回归（integration/mysql 业务/事务语义，H2）
mvn test -pl fluxion-test -am -Ph2-integration-test

# 真实 MySQL 集成（锁/身份 upsert 方言断言；需可用 MySQL，连不上即失败不静默跳过）
# 连接可用环境变量覆盖：FLUXION_TEST_MYSQL_HOST/PORT/DB/USER/PASSWORD
FLUXION_TEST_MYSQL_PORT=3306 FLUXION_TEST_MYSQL_PASSWORD=... \
  mvn test -pl fluxion-test -am -Pmysql-integration-test
```

**JDK 21 必需**。真实 MySQL 语义测试（`DistributedLockMySqlTest`、
`ExecutionIdentityUpsertMySqlTest`）只由 `mysql-integration-test` 承担；
连接配置见 `src/test/resources/application-test-real-mysql.yml`，要求新连接初始化
强制 `time_zone='+00:00'`（与 Hibernate jdbc.time_zone=
UTC 同口径，否则锁过期误判）。

---

## 📖 如何编写测试

### 组件测试

```java
package io.fluxion.test.core.schedule;

import org.junit.jupiter.api.Test;

class MyComponentTest {
    @Test
    void shouldDoSomething() {
        // Given / When / Then
    }
}
```

### MySQL 语义集成测试

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

schema 定义见 `src/test/resources/migration/`（Flyway）。

---

## 🔧 配置说明

| Profile | 说明 |
|---------|------|
| `regression-test` | 运行所有 `*Test`/`*Tests`，排除 `**/integration/mysql/**` |
| `h2-integration-test` | 只运行 `**/integration/mysql/**`，排除 `DistributedLockMySqlTest`（MySQL 原子 upsert 无法用 H2 表达） |

---

## 📝 命名规范

- **组件测试**: `*Test.java`
- **MySQL 集成测试**: `*MySqlTest.java`
- **测试方法**: `should*When*` 或 `test*` 格式

---

## ⚠️ 注意事项

1. **H2 不能替代 MySQL 原生语义，真实 MySQL 验证已闭环**：`DistributedLockMySqlTest` 与
   `ExecutionIdentityUpsertMySqlTest`（upsert 锁/身份幂等/会话时区方言断言）被
   `h2-integration-test` 显式排除，由 `mysql-integration-test` 在真实 MySQL 上承担
   （MySQL 8.4 + Connector/J + REPEATABLE READ，要求连接初始化强制 `time_zone='+00:00'`），
   详见 `docs/architecture/scheduling-reliability-audit.md` 的 R4。
2. 旧文档提及的 `ScheduleLeaseMySqlTest`、`ExecutionRecoveryMySqlTest`、`BrokerMultiNodeLeaseTest`、`LeaseBoundaryTest`、`BaseIntegrationTest` 已随 `ScheduleDelay → Execution` 合并删除，不存在于本仓库。
3. 本地编译可使用 `-DskipTests` 跳过测试。