# Fluxion Test Module

## 📁 模块结构

本模块集中存放所有层级的测试代码。

```
fluxion-test/src/test/java/io/fluxion/test/
│
├── support/                          # 测试基础设施
│   ├── base/                         #   基础测试类
│   │   ├── BaseIntegrationTest.java  #   集成测试基类
│   │   └── TestApplication.java    #   测试应用入口
│   │
│   └── environment/                  #   测试环境
│       └── LocalDistributedLock.java #   本地分布式锁（替代数据库锁）
│
├── core/                             # 核心模块测试
│   ├── schedule/                     #   调度组件测试
│   ├── execution/                    #   执行组件测试
│   └── broker/                       #   Broker 组件测试
│
├── integration/                      # 集成测试
│   └── mysql/                        #   MySQL 集成测试
│       ├── ScheduleLeaseMySqlTest.java
│       ├── ExecutionRecoveryMySqlTest.java
│       ├── DistributedLockMySqlTest.java
│       ├── BrokerMultiNodeLeaseTest.java
│       └── LeaseBoundaryTest.java
│
└── infrastructure/                   # 基础设施测试
    └── schedule/                     #   调度器测试
```

---

## 🧪 测试分层

| 层级 | 目录 | 特点 | 执行时间 | 依赖 |
|------|------|------|---------|------|
| **单元测试** | `core/` | 纯内存、无 Spring 上下文 | < 100ms/个 | JUnit |
| **集成测试** | `integration/mysql/` | MySQL 8 + Spring Boot | 秒级 | Docker/MySQL |

---

## 🚀 如何运行

### 运行全部测试
```bash
cd fluxion/fluxion-test
mvn test
```

### 运行回归测试（非 MySQL）
```bash
mvn test -pl fluxion-test -am -Pregression-test
```

### 运行 MySQL 集成测试
```bash
# 默认通过 Testcontainers 启动 MySQL 8
mvn test -pl fluxion-test -am -Pmysql-integration-test

# 无 Docker 时，使用外部 MySQL
export FLUXION_TEST_MYSQL_URL='jdbc:mysql://127.0.0.1:3306/fluxion_test'
export FLUXION_TEST_MYSQL_USERNAME=root
export FLUXION_TEST_MYSQL_PASSWORD='***'
mvn test -pl fluxion-test -am -Pmysql-integration-test
```

---

## 📖 如何编写测试

### 单元测试示例
```java
package io.fluxion.test.core.schedule;

import org.junit.jupiter.api.Test;

class MyCalculatorTest {
    
    @Test
    void shouldCalculateCorrectly() {
        // 纯 JUnit，无 Spring 上下文
    }
}
```

### MySQL 集成测试示例
```java
package io.fluxion.test.integration.mysql;

import io.fluxion.test.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = MySqlTestApplication.class)
@ActiveProfiles("test-mysql")
class MyMySqlTest extends AbstractMySqlIntegrationTest {
    
    @Test
    void shouldWorkWithMySQL() {
        // 自动连接 Testcontainers MySQL 或外部 MySQL
    }
}
```

---

## 🔧 配置说明

### 可用 Maven Profiles

| Profile | 说明 |
|---------|------|
| `regression-test` | 运行非 MySQL 回归测试（排除 `**/integration/mysql/**`） |
| `mysql-integration-test` | 仅运行 MySQL 集成测试 |

---

## 📝 命名规范

- **单元测试类**: `*Test.java`
- **MySQL 集成测试类**: `*MySqlTest.java`
- **测试方法**: `should*When*` 或 `test*` 格式

---

## ⚠️ 注意事项

1. **MySQL 集成测试**需要 Docker 或外部 MySQL 8
2. **ScheduleLease**、**ExecutionRecovery**、**DistributedLock** 必须在 MySQL 上测试（使用 `NOW(3)` 毫秒精度）
3. 本地测试可使用 `-DskipTests` 跳过测试编译
