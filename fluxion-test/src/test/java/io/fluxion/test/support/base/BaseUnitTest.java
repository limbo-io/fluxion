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

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * 【单元测试基类】
 * 
 * 作用：为纯单元测试提供基础能力
 * 
 * 特点：
 *   - 仅使用 Mockito，不加载 Spring 上下文
 *   - 纯内存测试，执行速度最快（毫秒级）
 *   - 适合测试单个类的方法逻辑、算法、工具类
 * 
 * 与 BaseIntegrationTest 的区别：
 *   ┌────────────────┬─────────────────────┬─────────────────────┐
 *   │ 特性           │ BaseUnitTest        │ BaseIntegrationTest │
 *   ├────────────────┼─────────────────────┼─────────────────────┤
 *   │ Spring 上下文   │ ❌ 不加载            │ ✅ 完整加载          │
 *   │ 数据库         │ ❌ 无                │ ✅ H2 内存库         │
 *   │ Broker/Worker  │ ❌ 无                │ ✅ 内嵌启动          │
 *   │ 执行速度        │ ⚡ 极快（毫秒）       │ 🐢 较慢（秒级）      │
 *   │ 测试范围        │ 单个类/方法          │ 模块间交互           │
 *   └────────────────┴─────────────────────┴─────────────────────┘
 * 
 * 使用场景：
 *   - 调度算法测试（ScheduleCalculator）
 *   - 工具类测试（日期计算、字符串处理）
 *   - 纯业务逻辑测试（无外部依赖）
 * 
 * 使用示例：
 * <pre>
 *   public class CronScheduleCalculatorTest extends BaseUnitTest {
 *       
 *       @InjectMocks
 *       private CronScheduleCalculator calculator;
 *       
 *       @Test
 *       void testCalculateNextTriggerTime() {
 *           LocalDateTime next = calculator.calculate(baseTime, "0 0 9 * * ?");
 *           assertEquals(LocalDateTime.of(2025, 1, 2, 9, 0), next);
 *       }
 *   }
 * </pre>
 * 
 * @author Fluxion Test Framework
 * @see io.fluxion.test.support.base.BaseIntegrationTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public abstract class BaseUnitTest {

    // ===== 通用常量 =====
    
    /** 默认超时时间 */
    protected static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    
    /** 轮询间隔 */
    protected static final Duration POLL_INTERVAL = Duration.ofMillis(50);

    // ===== 通用工具方法 =====

    /**
     * 等待指定时间（用于简单延迟场景）
     * 
     * @param duration 等待时长
     */
    protected void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("线程被中断", e);
        }
    }

    /**
     * 等待条件满足（轮询方式）
     * 
     * @param condition     条件判断
     * @param timeout       超时时间
     * @param pollInterval  轮询间隔
     * @return true=条件满足, false=超时
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
                throw new RuntimeException("等待时线程被中断", e);
            }
        }

        return false;
    }

    /**
     * 等待条件满足（使用默认轮询间隔）
     * 
     * @param condition 条件判断
     * @param timeout   超时时间
     * @return true=条件满足, false=超时
     */
    protected boolean waitForCondition(BooleanSupplier condition, Duration timeout) {
        return waitForCondition(condition, timeout, POLL_INTERVAL);
    }

    /**
     * 循环执行直到成功（带最大重试）
     * 
     * 用于测试一些可能需要多次尝试的操作
     * 
     * @param action      执行动作
     * @param maxRetries  最大重试次数
     * @param retryDelay  重试间隔
     * @return true=最终成功, false=达到最大重试次数仍失败
     */
    protected boolean retryUntilSuccess(BooleanSupplier action, 
                                         int maxRetries, 
                                         Duration retryDelay) {
        for (int i = 0; i <= maxRetries; i++) {
            if (action.getAsBoolean()) {
                return true;
            }
            if (i < maxRetries) {
                sleep(retryDelay);
            }
        }
        return false;
    }
}
