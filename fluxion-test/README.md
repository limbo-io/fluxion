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
│   ├── schedule/
│   │   ├── BacklogStrategyTest.java          # 积压不折叠（保留全部触发点）
│   │   ├── DelayedTaskSchedulerTest.java
│   │   └── PeriodicTaskSchedulerTest.java
│   └── worker/
│       ├── WorkerCpuLoadSelectionTest.java
│       └── WorkerLoadBalancingTest.java
│
├── integration/                      # MySQL 语义集成测试
│   └── mysql/
│       ├── MySqlTestApplication.java          # Spring 入口
│       ├── AbstractMySqlIntegrationTest.java  # 空 abstract 基类（类型标记）
│       ├── ExecutionIdentityConstraintTest.java   # (triggerId,triggerAt) 唯一约束
│       ├── JobFaultStatePersistenceTest.java      # Job 容错字段持久化
│       ├── JobLeaseServiceTest.java                # Job 租约/fencing/RETRY_WAIT
│       ├── WorkerOfflineMigrationTest.java         # Worker 下线 Job 级接管
│       └── DistributedLockMySqlTest.java           # MySQL 原子锁（h2 profile 排除）
│
└── JsonTest.java / ReflectionTest.java          # 库级工具回归
```

> 历史空目录 `core/execution`、`core/faulttolerance/{failover,retry,store,timeout}`、`core/job/fault` 下没有任何测试文件。

---

## 🧪 测试分层

| 层级 | 目录 | 特点 | 执行 profile |
|------|------|------|------------|
| **组件回归** | `core/` | H2、Spring 上下文 | `regression-test` |
| **MySQL 语义集成** | `integration/mysql/` | H2 MySQL 兼容模式运行 | `h2-integration-test`（排除 `DistributedLockMySqlTest`） |

---

## 🚀 如何运行

```bash
# 回归测试（排除 integration/mysql 包）
mvn test -pl fluxion-test -am -Pregression-test

# 集成回归（只跑 integration/mysql 包，H2）
mvn test -pl fluxion-test -am -Ph2-integration-test
```

**JDK 21 必需**。仓库没有 `mysql-integration-test` profile；`DistributedLockMySqlTest` 需要
真实 MySQL 时参考 `src/test/resources/application-test-mysql.yml` 配置外部环境（当前无现成
Maven 入口）。

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

schema 定义见 `src/test/resources/schema-mysql.sql` / `schema-h2.sql` / `migration/`。

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

1. **H2 不能替代 MySQL 原生语义**：`DistributedLockMySqlTest` 被 `h2-integration-test` 显式排除，生产 MySQL 并发验证尚未闭环。
2. 旧文档提及的 `ScheduleLeaseMySqlTest`、`ExecutionRecoveryMySqlTest`、`BrokerMultiNodeLeaseTest`、`LeaseBoundaryTest`、`BaseIntegrationTest` 已随 `ScheduleDelay → Execution` 合并删除，不存在于本仓库。
3. 本地编译可使用 `-DskipTests` 跳过测试。