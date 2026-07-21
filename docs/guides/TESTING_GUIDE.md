# Fluxion 测试指南

本文档介绍 Fluxion 任务调度平台的测试流程和使用方法。

## 快速开始

```bash
# 1. 执行非 MySQL 回归测试
mvn test -pl fluxion-test -am -Pregression-test

# 2. 执行 MySQL 并发 SQL 集成测试（需要 Docker 或外部 MySQL）
mvn test -pl fluxion-test -am -Pmysql-integration-test

# 3. 执行全部测试并生成覆盖率报告
mvn test -pl fluxion-test -Pall-tests jacoco:report
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
6. 重试间隔配置
7. 工作流调度配置
8. 核心 API 验证

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

Fluxion 测试采用**六层金字塔**架构：

```
Layer 6: 完整调度链路测试 (End-to-End)
Layer 5: 跨模块集成测试 (Cross-Module)
Layer 4: 模块内集成测试 (Module-Integration)
Layer 3: 组件单元测试 (Component)
Layer 2: 工具类单元测试 (Utility)
Layer 1: 基础契约测试 (Contract)
```

## 测试基类体系

```
BaseTest
  ├── FastUnitTest（无 Spring 上下文）
  └── SpringBaseTest（@SpringBootTest）
        ├── ModuleTest
        │     ├── WorkerModuleTest
        │     └── ServerModuleTest
        ├── CrossModuleTest
        │     ├── WorkerServerLinkTest
        │     ├── SchedulingLinkTest
        │     └── FaultToleranceLinkTest
        └── EndToEndTest
              ├── SimpleJobE2ETest
              ├── BroadcastJobE2ETest
              └── WorkflowE2ETest
```

## 测试命名约定

| 测试类型 | 类名后缀 | 示例 |
|---------|---------|------|
| 快速单元测试 | `*Test` | `RetryStrategyTest` |
| 模块测试 | `*ModuleTest` | `JobExecutionModuleTest` |
| 集成测试 | `*IntegrationTest` | `FaultToleranceLinkTest` |
| 链路测试 | `*LinkTest` | `TriggerScheduleLinkTest` |
| E2E 测试 | `*E2ETest` | `WorkflowE2ETest` |

## 详细执行命令

### 1. 快速单元测试（默认）

```bash
# 执行所有快速单元测试（不包括模块、集成、链路、E2E 测试）
mvn test -pl fluxion-test

# 或使用默认 profile（效果相同）
mvn test -pl fluxion-test -Pdefault
```

### 2. 模块测试

```bash
# 执行所有模块测试
mvn test -pl fluxion-test -Pmodule-test

# 执行特定模块测试
mvn test -pl fluxion-test -Dtest=JobExecutionModuleTest
```

### 3. 集成测试

```bash
# 执行所有集成测试
mvn test -pl fluxion-test -Pintegration-test

# 执行特定的容错集成测试
mvn test -pl fluxion-test -Dtest=FaultToleranceLinkTest
```

### 4. 链路测试

```bash
# 执行所有链路测试
mvn test -pl fluxion-test -Plink-test

# 执行特定链路测试
mvn test -pl fluxion-test -Dtest="*TriggerScheduleLinkTest,*ScheduleExecutionLinkTest"
```

### 5. E2E 测试

```bash
# 执行所有 E2E 测试
mvn test -pl fluxion-test -Pe2e

# 执行工作流 E2E 测试
mvn test -pl fluxion-test -Dtest=WorkflowE2ETest
```

### 6. 全部测试

```bash
# 执行所有测试
mvn test -pl fluxion-test -Pall-tests

# 执行所有测试并生成 JaCoCo 覆盖率报告
mvn test -pl fluxion-test -Pall-tests jacoco:report

# 查看覆盖率报告
open fluxion-test/target/site/jacoco/index.html
```

## 结合 `-am` 参数

在根目录执行时，建议添加 `-am`（also-make）参数，确保依赖模块先被编译：

```bash
# 完整命令示例
mvn test -pl fluxion-test -am -Plink-test
```

## TestLauncher 使用

`TestLauncher` 提供便捷的测试套件执行方式：

```bash
# 查看可用的测试套件
java -cp fluxion-test/target/test-classes io.fluxion.test.launcher.TestLauncher

# 执行调度相关测试套件
java -cp fluxion-test/target/test-classes io.fluxion.test.launcher.TestLauncher scheduling
# 输出: mvn test -Dtest=*TriggerScheduleLinkTest,*ScheduleExecutionLinkTest

# 执行容错测试套件
java -cp fluxion-test/target/test-classes io.fluxion.test.launcher.TestLauncher fault-tolerance
```

### 可用的测试套件

| 套件名称 | 说明 |
|---------|------|
| `scheduling` | 调度链路测试（Trigger → Schedule → Execution） |
| `rpc` | RPC 通信测试（Server ↔ Worker） |
| `execution` | 任务执行测试 |
| `fault-tolerance` | 容错机制测试 |
| `workflow` | 工作流编排测试 |
| `all-link` | 所有链路和集成测试 |
| `all-e2e` | 所有 E2E 测试 |

## CI/CD 流水线

项目已配置 GitHub Actions 工作流（`.github/workflows/test.yml`）：

```
Push/PR → unit-test → module-test → link-test → e2e-test → coverage
         (1-5秒)     (5-15秒)      (15-30秒)   (60+秒)    (报告)
```

### 本地模拟 CI 执行

```bash
# 完整 CI 流程（本地模拟）
mvn clean
mvn test -pl fluxion-test -am --batch-mode                    # 单元测试
mvn test -pl fluxion-test -am -Pmodule-test --batch-mode      # 模块测试
mvn test -pl fluxion-test -am -Plink-test --batch-mode        # 链路测试
mvn test -pl fluxion-test -am -Pe2e --batch-mode              # E2E 测试
mvn test -pl fluxion-test -am -Pall-tests jacoco:report       # 覆盖率
```

## 编写新测试

### 1. 快速单元测试

```java
public class MyUtilityTest extends FastUnitTest {
    @Test
    void testMyUtility() {
        // Given
        String input = "hello";

        // When
        String result = MyUtility.capitalize(input);

        // Then
        assertThat(result).isEqualTo("Hello");
    }
}
```

### 2. 模块测试

```java
public class MyWorkerModuleTest extends WorkerModuleTest {
    @Test
    void testWorkerFunction() {
        // 可以直接使用 fluxionWorker
        assertThat(fluxionWorker).isNotNull();
        assertThat(fluxionWorker.isRunning()).isTrue();

        // 使用 testDataFactory 创建测试数据
        var schedule = testDataFactory.createDelaySchedule(HelloExecutor.NAME, 1000);
    }
}
```

### 3. 链路测试

```java
public class MyLinkTest extends CrossModuleTest {
    @Test
    void testScheduleToExecution() {
        // Given
        var schedule = testDataFactory.createDelaySchedule(HelloExecutor.NAME, 500);

        // When
        submitSchedule(schedule);

        // Then
        boolean received = waitForTaskReceived(schedule.getId(), Duration.ofSeconds(10));
        assertThat(received).isTrue();
    }
}
```

### 4. E2E 测试

```java
public class MyE2ETest extends EndToEndTest {
    @Test
    void testFullWorkflow() {
        // 可以验证完整的调度链路
        verifyScheduleLink(scheduleId);
    }
}
```

## 常用工具方法

### 等待工具

```java
// 等待指定时间
sleep(Duration.ofMillis(500));

// 等待条件满足（默认 30 秒超时）
waitForCondition(() -> someCondition());

// 等待条件满足（自定义超时）
waitForCondition(() -> someCondition(), Duration.ofSeconds(10));
```

### 断言工具

```java
// 任务状态断言
FluxionAssertions.assertThat(execution).hasStatus(ExecutionStatus.SUCCESS);
FluxionAssertions.assertThat(execution).isSuccess();
FluxionAssertions.assertThat(execution).isFailed();

// Schedule 断言
FluxionAssertions.assertThat(schedule).hasRetryCount(3);
FluxionAssertions.assertThat(schedule).isEnabled();

// 异步等待断言
FluxionAssertions.assertEventually(
    () -> testDataFactory.isExecutionCompleted(executionId),
    Duration.ofSeconds(10),
    "Execution did not complete in time"
);
```

## 常见问题

### 1. 测试启动慢

使用 `FastUnitTest` 代替 `SpringBaseTest`，避免 Spring 上下文启动。

### 2. 测试间数据污染

基类已自动清理数据。如需手动清理：
```java
@AfterEach
void tearDown() {
    testDataFactory.clearAll();
    FailingExecutor.clearAll();
}
```

### 3. 异步测试不稳定

使用 `waitForCondition()` 代替 `Thread.sleep()`。

### 4. 覆盖率不达标

```bash
open fluxion-test/target/site/jacoco/index.html
```

## 测试文件目录结构

```
fluxion-test/src/test/java/io/fluxion/test/
├── BaseTest.java                    # 最基础测试类
├── FastUnitTest.java                # 快速单元测试基类
├── SpringBaseTest.java              # SpringBootTest 基类
├── ModuleTest.java                  # 模块测试基类
├── WorkerModuleTest.java            # Worker 模块基类
├── ServerModuleTest.java            # Server 模块基类
├── CrossModuleTest.java             # 跨模块测试基类
├── EndToEndTest.java                # E2E 测试基类
├── assertj/
│   └── FluxionAssertions.java       # 自定义断言库
├── data/
│   └── TestDataFactory.java         # 测试数据工厂
├── env/
│   └── EmbeddedFluxionEnvironment.java # 测试环境管理器
├── executors/                       # 测试执行器集合
├── link/                            # 调度链路测试
├── module/                          # 模块测试
├── integration/                     # 集成测试
├── e2e/                             # E2E 测试
└── launcher/
    └── TestLauncher.java            # 测试启动器
```
