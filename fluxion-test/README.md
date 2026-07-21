# Fluxion Test Module

## 📁 模块结构

本模块集中存放所有层级的测试代码，便于统一管理和维护。

```
fluxion-test/src/test/java/io/fluxion/test/
│
├── support/                          # 【测试基础设施】测试共享组件
│   ├── base/                         #   基础测试类
│   │   ├── BaseUnitTest.java         #     单元测试基类（Mockito）
│   │   ├── BaseIntegrationTest.java  #   集成测试基类（Spring Boot）
│   │   └── TestApplication.java      #     测试应用入口
│   │
│   ├── environment/                  #   测试环境
│   │   ├── EmbeddedFluxionEnvironment.java  # 内嵌 Broker + Worker
│   │   ├── LocalDistributedLock.java   #   本地分布式锁（替代数据库锁）
│   │   └── TestProfiles.java           #   测试配置常量
│   │
│   ├── data/                         #   测试数据
│   │   └── TestDataFactory.java      #     测试数据工厂
│   │
│   ├── assertions/                   #   断言工具
│   │   └── JobExecutionAssert.java   #     Job 执行断言
│   │
│   ├── executors/                    #   测试执行器
│   │   ├── SimpleTestExecutor.java   #     简单成功执行器
│   │   ├── CounterExecutor.java      #     计数执行器（验证顺序）
│   │   └── FailingExecutor.java      #     失败模拟执行器（验证重试）
│   │
│   └── launcher/                     #   测试启动器
│       └── TestLauncher.java         #     测试套件运行器
│
├── unit/                             # 【单元测试】快速纯内存测试
│   ├── server/
│   │   ├── schedule/                 #   调度计算测试
│   │   │   ├── CronScheduleCalculatorTest.java
│   │   │   ├── FixedRateScheduleCalculatorTest.java
│   │   │   ├── FixedDelayScheduleCalculatorTest.java
│   │   │   ├── PeriodicTaskSchedulerTest.java
│   │   │   └── DelayedTaskSchedulerTest.java
│   │   │
│   │   ├── execution/fault/          #   容错组件测试
│   │   │   ├── DefaultFaultToleranceCoordinatorTest.java
│   │   │   ├── ExecutionStateTest.java
│   │   │   ├── retry/
│   │   │   │   └── ExponentialBackoffRetryStrategyTest.java
│   │   │   ├── failover/
│   │   │   │   └── DefaultFailoverManagerTest.java
│   │   │   └── timeout/
│   │   │       └── TimingWheelTimeoutManagerTest.java
│   │   │
│   │   └── workflow/                 #   工作流测试
│   │       └── WorkflowTest.java
│   │
│   └── worker/                       #   Worker 组件测试
│
├── integration/                      # 【集成测试】模块间交互测试
│   ├── executor/
│   │   └── ExecutorIntegrationTest.java    # EXECUTOR 类型任务执行链路
│   │
│   ├── fault/
│   │   ├── RetryIntegrationTest.java       # 失败重试机制
│   │   └── FaultToleranceIntegrationTest.java # 容错组件
│   │
│   ├── schedule/                     #   调度集成测试
│   ├── worker/                         #   Worker 生命周期测试
│   ├── workflow/                       #   工作流执行测试
│   └── RegressionTestSuite.java        # 回归测试套件
│
├── contract/                         # 【契约测试】（预留）
│   └── BrokerWorkerProtocolTest.java   #   Broker-Worker API 契约
│
├── e2e/                              # 【端到端测试】（预留）
│   └── JobLifecycleE2ETest.java        #   完整任务生命周期
│
└── performance/                      # 【性能测试】（预留）
    └── SchedulingThroughputTest.java   # 调度吞吐量测试
```

---

## 🧪 测试分层

| 层级 | 目录 | 特点 | 执行时间 | 依赖 |
|------|------|------|---------|------|
| **单元测试** | `unit/` | 纯内存、无 Spring 上下文 | < 100ms/个 | Mockito |
| **集成测试** | `integration/` | 内嵌 Broker + Worker + H2 | 秒级 | Spring Boot |
| **契约测试** | `contract/` | API 接口契约验证 | 秒级 | Spring Boot |
| **E2E 测试** | `e2e/` | 完整业务流程 | 分钟级 | Spring Boot + 前端 |
| **性能测试** | `performance/` | 吞吐量、延迟基准 | 分钟级 | JMH/Gatling |

---

## 🚀 如何运行

### 运行全部测试
```bash
cd fluxion/fluxion-test
mvn test
```

### 运行指定层级测试
```bash
# 仅单元测试
mvn test -Dtest="io.fluxion.test.unit.**.**"

# 仅集成测试
mvn test -Dtest="io.fluxion.test.integration.**.**"

# 回归测试套件
mvn test -Dtest=RegressionTestSuite
```

### 使用 TestLauncher 获取命令
```bash
# 列出可用套件
mvn exec:java -Dexec.mainClass="io.fluxion.test.support.launcher.TestLauncher"

# 运行指定套件
TestLauncher.runSuite("fault");  // 获取运行容错测试的 Maven 命令
```

---

## 📖 如何编写测试

### 单元测试示例
```java
package io.fluxion.test.unit.server.schedule;

import io.fluxion.test.support.base.BaseUnitTest;
import org.junit.jupiter.api.Test;

class MyCalculatorTest extends BaseUnitTest {
    
    @Test
    void shouldCalculateCorrectly() {
        // 纯 Mockito，无 Spring 上下文
    }
}
```

### 集成测试示例
```java
package io.fluxion.test.integration.executor;

import io.fluxion.test.support.base.BaseIntegrationTest;
import io.fluxion.test.support.executors.SimpleTestExecutor;
import org.junit.jupiter.api.Test;

class MyIntegrationTest extends BaseIntegrationTest {
    
    @Test
    void shouldExecuteJob() {
        // 自动启动 Broker + Worker
        // 使用 testDataFactory 创建测试数据
        // 使用 waitForCondition 等待异步结果
    }
}
```

---

## 📁 关键文件说明

### 测试基础设施 (support/)

| 文件 | 作用 | 使用场景 |
|------|------|---------|
| `BaseUnitTest` | 单元测试基类 | 纯算法/工具类测试 |
| `BaseIntegrationTest` | 集成测试基类 | 需要 Spring 上下文的测试 |
| `EmbeddedFluxionEnvironment` | 内嵌环境启动 | 自动启动 Broker + Worker |
| `TestDataFactory` | 测试数据工厂 | 快速创建 Schedule/RetryOption |
| `SimpleTestExecutor` | 简单执行器 | 验证正常执行流程 |
| `FailingExecutor` | 失败执行器 | 验证重试机制 |
| `CounterExecutor` | 计数执行器 | 验证执行顺序 |

---

## 🔧 配置说明

### application-test.yml
测试专用配置文件，使用 H2 内存数据库替代 MySQL。

### DirtiesContext
`BaseIntegrationTest` 默认配置 `@DirtiesContext`，确保每个测试类执行后清理 Spring 上下文。

---

## 📝 命名规范

- **单元测试类**: `*Test.java` (如 `CronScheduleCalculatorTest.java`)
- **集成测试类**: `*IntegrationTest.java` (如 `RetryIntegrationTest.java`)
- **E2E 测试类**: `*E2ETest.java` (如 `JobLifecycleE2ETest.java`)
- **测试方法**: `should*When*` 或 `test*` 格式

---

## ⚠️ 注意事项

1. **单元测试**不要依赖 Spring 上下文，使用 `BaseUnitTest`
2. **集成测试**会自动清理数据，不需要手动删库
3. **执行器**使用静态变量存储记录，测试结束后会自动清理
4. **超时设置**: 默认 30s，复杂测试可使用 `LONG_TIMEOUT`
