package io.fluxion.test.integration;

import io.fluxion.test.integration.executor.ExecutorIntegrationTest;
import io.fluxion.test.integration.fault.FaultToleranceIntegrationTest;
import io.fluxion.test.integration.fault.RetryIntegrationTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * 【回归测试套件】
 * 
 * 作用：按套件运行一组关键集成测试，用于：
 *   - 代码提交前的快速验证
 *   - CI/CD 流水线中的回归检查
 *   - 发布前的全量验证
 * 
 * 包含测试：
 *   ┌─────────────────────────────────────┐
 *   │  ExecutorIntegrationTest            │
 *   │  - 简单任务执行                     │
 *   │  - 并发调度执行                     │
 *   │  - 执行时间精度                     │
 *   ├─────────────────────────────────────┤
 *   │  RetryIntegrationTest               │
 *   │  - 重试直到成功                     │
 *   │  - 重试次数配置                     │
 *   │  - 重试间隔验证                     │
 *   ├─────────────────────────────────────┤
 *   │  FaultToleranceIntegrationTest      │
 *   │  - 执行注册/完成                    │
 *   │  - Worker 离线迁移                  │
 *   └─────────────────────────────────────┘
 * 
 * 执行方式：
 *   1. IDE: 直接运行此类
 *   2. Maven: mvn test -Dtest=RegressionTestSuite
 *   3. Launcher: TestLauncher.runSuite("regression")
 * 
 * @author Fluxion Test Framework
 * @see io.fluxion.test.support.launcher.TestLauncher
 */
@Suite
@SelectClasses({
    // 执行器调度测试
    ExecutorIntegrationTest.class,
    
    // 重试机制测试
    RetryIntegrationTest.class,
    
    // 容错能力测试
    FaultToleranceIntegrationTest.class
})
public class RegressionTestSuite {
    // 套件类，无需实现
    // @Suite 注解自动收集和执行 @SelectClasses 指定的测试类
}
