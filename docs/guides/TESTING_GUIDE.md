# Fluxion 测试指南

本文档介绍 Fluxion 任务调度平台的测试流程和使用方法。

## 快速开始

```bash
# 1. 执行非 MySQL 回归测试
mvn test -pl fluxion-test -am -Pregression-test

# 2. 执行 MySQL 并发 SQL 集成测试（需要 Docker 或外部 MySQL）
mvn test -pl fluxion-test -am -Pmysql-integration-test
```

## 回归测试（发布前验证）

`regression-test` profile 执行除 `integration/mysql` 外的 `*Test`/`*Tests`。使用 `-am` 确保测试运行的是本次构建出的 server/worker 模块，而不是本地 Maven 仓库中的旧依赖。

```bash
# 运行回归测试
mvn test -pl fluxion-test -am -Pregression-test
```

回归测试覆盖以下场景：
1. 延迟任务调度配置
2. Cron 表达式调度配置
3. 任务分发配置
4. Worker 执行配置
5. 失败重试机制
6. 工作流调度配置
7. 核心 API 验证

## MySQL 集成测试

lease fencing、数据库锁和 execution recovery 使用 MySQL 专有 SQL，必须在 MySQL 8 上验证，不能使用 H2 替代：

```bash
# 默认通过 Testcontainers 启动 MySQL 8
mvn test -pl fluxion-test -am -Pmysql-integration-test

# 无 Docker 时，使用外部 MySQL
export FLUXION_TEST_MYSQL_URL='jdbc:mysql://127.0.0.1:3306/fluxion_test?useSSL=false&serverTimezone=UTC'
export FLUXION_TEST_MYSQL_USERNAME=root
export FLUXION_TEST_MYSQL_PASSWORD='***'
mvn test -pl fluxion-test -am -Pmysql-integration-test
```

该 profile 覆盖数据库锁竞争、schedule lease 续租/接管和 execution recovery 的 MySQL 行为。

## 测试架构

Fluxion 测试采用**分层架构**：

```
Layer 1: 单元测试 (Unit Tests)
Layer 2: MySQL 集成测试 (MySQL Integration Tests)
```

## 可用 Maven Profiles

| Profile | 命令 | 说明 |
|---------|------|------|
| `regression-test` | `mvn test -pl fluxion-test -am -Pregression-test` | 运行非 MySQL 测试 |
| `mysql-integration-test` | `mvn test -pl fluxion-test -am -Pmysql-integration-test` | 仅运行 MySQL 集成测试 |

## MySQL 集成测试清单

位于 `fluxion-test/src/test/java/io/fluxion/test/integration/mysql/`：

| 测试类 | 覆盖场景 |
|--------|----------|
| `ScheduleLeaseMySqlTest` | Schedule lease 续租、接管、验证 |
| `ExecutionRecoveryMySqlTest` | Execution recovery、超时重试 |
| `DistributedLockMySqlTest` | 分布式锁并发、过期、错误 unlock |
| `BrokerMultiNodeLeaseTest` | 双 Broker lease Command 链路 |
| `LeaseBoundaryTest` | Lease 参数边界、优雅停机 |

## 编写新测试

### 1. 单元测试

```java
package io.fluxion.test.core.schedule;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MyUtilityTest {
    @Test
    void shouldCalculateCorrectly() {
        // Given
        String input = "hello";

        // When
        String result = MyUtility.capitalize(input);

        // Then
        assertThat(result).isEqualTo("Hello");
    }
}
```

### 2. MySQL 集成测试

```java
package io.fluxion.test.integration.mysql;

import io.fluxion.test.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = MySqlTestApplication.class)
@ActiveProfiles("test-mysql")
class MyMySqlTest extends AbstractMySqlIntegrationTest {

    @Test
    void shouldWorkWithMySQL() {
        // 使用真实 MySQL 数据库
    }
}
```

## CI/CD 流水线

项目已配置 GitHub Actions 工作流：

### `.github/workflows/ci.yml`

```
Push/PR → compile → test → regression-test
```

### `.github/workflows/test.yml`

```
Push/PR → unit-test → regression-test → mysql-integration-test
```

### 本地模拟 CI 执行

```bash
# 完整 CI 流程（本地模拟）
mvn clean
mvn test -pl fluxion-test -am --batch-mode                    # 单元测试
mvn test -pl fluxion-test -am -Pregression-test --batch-mode  # 回归测试
mvn test -pl fluxion-test -am -Pmysql-integration-test        # MySQL 集成测试
```

## 测试清理记录

### 已删除的过期测试

| 测试类 | 删除原因 | 替代方案 |
|--------|----------|----------|
| `FencingConditionMySqlTest` | 仅测试 MySQL NOW(3) 临时表，不覆盖业务逻辑 | 使用真实 lease/lock 测试 |
| `ExecutionRegistrationTest` | 纯 DTO builder 测试，无业务覆盖 | 由集成测试覆盖 |
| `ExecutionResultTest` | 纯 DTO builder 测试，无业务覆盖 | 由集成测试覆盖 |
| `ExecutionStateTest` | 纯 enum 测试，无业务覆盖 | 由集成测试覆盖 |
| `ErrorCategoryTest` | 纯 enum 测试，无业务覆盖 | 由集成测试覆盖 |

### 已重写的测试

| 测试类 | 重写内容 |
|--------|----------|
| `ScheduleTaskTest` | 使用 FakeTimer 替代 Thread.sleep |

### 已新增的测试

| 测试类 | 覆盖场景 |
|--------|----------|
| `BrokerMultiNodeLeaseTest` | T2.1: 2 Broker Command → Handler → Database 链路 |
| `LeaseBoundaryTest` | T2.2/T2.3: Lease 参数边界、优雅停机 |

## 注意事项

1. **ScheduleLease**、**ExecutionRecovery**、**DistributedLock** 必须在 MySQL 上测试
2. `regression-test` 和 `mysql-integration-test` 是 CI 中唯二可用的 profile
3. 测试文件命名遵循 `*Test.java` 或 `*Tests.java` 模式
