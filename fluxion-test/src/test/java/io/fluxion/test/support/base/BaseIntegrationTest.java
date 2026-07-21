/*
 * Copyright 2025-2030 Limbo Team (https://github.com/limbo-io).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxion.test.support.base;

import io.fluxion.test.support.data.TestDataFactory;
import io.fluxion.test.support.environment.EmbeddedFluxionEnvironment;
import io.fluxion.test.support.environment.TestProfiles;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * 【集成测试基类】
 * 
 * 作用：为集成测试提供完整的测试环境
 * 
 * 自动能力：
 *   1. 加载 Spring Boot 测试上下文
 *   2. 启动内嵌 Broker + Worker（通过 EmbeddedFluxionEnvironment）
 *   3. 每个测试方法前后自动清理数据
 *   4. 提供常用等待工具方法
 * 
 * 启动过程（@BeforeEach）：
 *   ┌──────────────────────────────────────────────────────┐
 *   │ 1. Spring Boot 上下文初始化                          │
 *   │ 2. Broker 自动启动                                   │
 *   │ 3. Worker 自动注册到 Broker                          │
 *   │ 4. 等待环境就绪（waitForReady）                      │
 *   │ 5. 清理历史测试数据（clearData/clearAll）            │
 *   └──────────────────────────────────────────────────────┘
 * 
 * 环境组成：
 *   - 嵌入式 H2 数据库（替代 MySQL）
 *   - 单节点 Broker（调度服务）
 *   - 单节点 Worker（执行服务）
 *   - 内存分布式锁（LocalDistributedLock）
 * 
 * 使用场景：
 *   - 任务调度端到端测试（创建→调度→执行→验证）
 *   - Worker 交互测试（注册、心跳、任务分发）
 *   - 重试、容错机制测试
 * 
 * 使用示例：
 * <pre>
 *   public class MyIntegrationTest extends BaseIntegrationTest {
 *       
 *       @Test
 *       void testJobExecution() {
 *           // 1. 创建测试数据
 *           Schedule schedule = testDataFactory.createExecutorDelaySchedule("myExecutor", 1000);
 *           
 *           // 2. 提交调度（通过 CQRS Command）
 *           scheduleCmdHandler.handle(new ScheduleSaveCmd(schedule));
 *           
 *           // 3. 等待执行完成
 *           boolean completed = waitForCondition(
 *               () -> SimpleTestExecutor.getExecutionCount() > 0,
 *               DEFAULT_TIMEOUT
 *           );
 *           
 *           // 4. 验证结果
 *           assertTrue(completed);
 *       }
 *   }
 * </pre>
 * 
 * @author Devil
 * @see io.fluxion.test.support.environment.EmbeddedFluxionEnvironment
 * @see io.fluxion.test.support.data.TestDataFactory
 * @see io.fluxion.test.support.base.BaseUnitTest
 */
@Slf4j
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles(TestProfiles.TEST_PROFILE)
// 每个测试类结束后清理 Spring 上下文（确保测试隔离）
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class BaseIntegrationTest {

    /**
     * 嵌入式测试环境（自动注入）
     * 
     * 包含：Broker、Worker、环境生命周期管理
     */
    @Autowired
    protected EmbeddedFluxionEnvironment embeddedEnvironment;

    /**
     * 测试数据工厂（自动注入）
     * 
     * 用于快速创建 Schedule、RetryOption 等测试对象
     */
    @Autowired
    protected TestDataFactory testDataFactory;

    // ===== 超时时间常量 =====
    
    /** 默认等待超时（一般操作） */
    protected static final Duration DEFAULT_TIMEOUT = TestProfiles.TIMEOUT_DEFAULT;
    
    /** 短等待超时（快速验证） */
    protected static final Duration SHORT_TIMEOUT = TestProfiles.TIMEOUT_SHORT;
    
    /** 长等待超时（复杂操作） */
    protected static final Duration LONG_TIMEOUT = TestProfiles.TIMEOUT_LONG;

    /**
     * 测试前置处理
     * 
     * 执行顺序：
     *   1. 确保环境已启动（如未启动则触发 start）
     *   2. 等待环境就绪（阻塞直到 Broker + Worker 就绪）
     *   3. 清理历史测试数据（执行器记录、测试对象注册表）
     */
    @BeforeEach
    void setUp() throws InterruptedException {
        log.info("[BaseIntegrationTest] ========== 测试准备阶段 ==========");

        // 步骤1: 确保环境启动
        if (!embeddedEnvironment.isStarted()) {
            log.info("[BaseIntegrationTest] 启动嵌入式环境...");
            embeddedEnvironment.start();
        }

        // 步骤2: 等待就绪（最多10秒）
        log.info("[BaseIntegrationTest] 等待环境就绪...");
        boolean ready = embeddedEnvironment.waitForReady(TestProfiles.ENV_READY_TIMEOUT_MS);
        if (!ready) {
            throw new IllegalStateException("嵌入式环境在 " + TestProfiles.ENV_READY_TIMEOUT_MS + "ms 内未就绪");
        }
        log.info("[BaseIntegrationTest] 环境已就绪");

        // 步骤3: 清理历史数据
        log.info("[BaseIntegrationTest] 清理历史测试数据...");
        embeddedEnvironment.clearData();      // 清理执行器记录
        testDataFactory.clearAll();          // 清理测试对象注册表

        log.info("[BaseIntegrationTest] ========== 测试准备完成 ==========");
    }

    /**
     * 测试后置处理
     * 
     * 清理当前测试产生的数据，避免影响其他测试
     */
    @AfterEach
    void tearDown() {
        log.info("[BaseIntegrationTest] ========== 测试清理阶段 ==========");
        
        embeddedEnvironment.clearData();
        testDataFactory.clearAll();
        
        log.info("[BaseIntegrationTest] ========== 测试清理完成 ==========");
    }

    // ===== 通用工具方法 =====

    /**
     * 休眠指定时间
     * 
     * 使用场景：需要简单延迟等待（不推荐用于异步结果验证）
     * 
     * @param duration 休眠时长
     */
    protected void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("线程休眠被中断", e);
        }
    }

    /**
     * 等待条件满足（轮询方式）
     * 
     * 使用场景：等待异步操作完成（如任务执行、状态变更）
     * 
     * 工作原理：
     *   1. 每隔 pollInterval 检查一次 condition
     *   2. 条件满足返回 true
     *   3. 超时返回 false
     * 
     * @param condition     条件判断（返回 true 表示满足）
     * @param timeout       最大等待时间
     * @param pollInterval  轮询检查间隔
     * @return true=条件已满足, false=超时
     */
    protected boolean waitForCondition(BooleanSupplier condition,
                                        Duration timeout,
                                        Duration pollInterval) {
        long endTime = System.currentTimeMillis() + timeout.toMillis();

        while (System.currentTimeMillis() < endTime) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(pollInterval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待条件时线程被中断", e);
            }
        }

        return false;
    }

    /**
     * 等待条件满足（使用默认轮询间隔 100ms）
     * 
     * @param condition 条件判断
     * @param timeout   最大等待时间
     * @return true=条件已满足, false=超时
     */
    protected boolean waitForCondition(BooleanSupplier condition, Duration timeout) {
        return waitForCondition(condition, timeout, Duration.ofMillis(100));
    }

    /**
     * 提交调度命令（封装通用提交逻辑）
     * 
     * 注意：需要具体的 CommandHandler，这里仅作示例
     * 
     * @param schedule Schedule 对象
     */
    protected void submitSchedule(io.fluxion.server.core.schedule.Schedule schedule) {
        // TODO: 需要注入具体的 CommandHandler
        // scheduleCommandService.save(schedule);
        log.info("[BaseIntegrationTest] 提交调度: id={}", schedule.getId());
    }
}
