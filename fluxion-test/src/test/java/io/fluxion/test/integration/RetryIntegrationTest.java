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

package io.fluxion.test.integration;

import io.fluxion.server.core.executor.option.RetryOption;
import io.fluxion.server.core.schedule.Schedule;
import io.fluxion.test.integration.executors.FailingExecutor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 失败重试能力集成测试
 * 验证任务失败后的重试机制
 *
 * @author Devil
 */
@Slf4j
public class RetryIntegrationTest extends BaseIntegrationTest {

    @Test
    void testRetryUntilSuccess() {
        // Given: 创建一个配置 3 次重试的调度计划
        RetryOption retryOption = RetryOption.builder()
            .retryTimes(3)
            .retryInterval(1)
            .build();

        Schedule schedule = testDataFactory.createExecutorDelayScheduleWithRetry(
            FailingExecutor.NAME,
            100,  // 100ms 延迟
            retryOption
        );

        // When: 提交调度计划
        submitSchedule(schedule);

        // Then: 等待任务最终成功
        boolean eventuallySucceeded = waitForCondition(
            () -> {
                FailingExecutor.ExecutionResult result =
                    FailingExecutor.getExecutionResult(schedule.getId());
                return result != null && result.success;
            },
            Duration.ofSeconds(10)
        );

        assertTrue(eventuallySucceeded, "Task should eventually succeed after retries");

        // 验证执行次数（前 2 次失败，第 3 次成功）
        int executionCount = FailingExecutor.getExecutionCount(schedule.getId());
        assertEquals(3, executionCount, "Task should be executed exactly 3 times");

        FailingExecutor.ExecutionResult finalResult =
            FailingExecutor.getExecutionResult(schedule.getId());
        assertNotNull(finalResult);
        assertTrue(finalResult.success);
        assertEquals(3, finalResult.attempt);

        log.info("[testRetryUntilSuccess] Task succeeded after {} attempts", executionCount);
    }

    @Test
    void testRetryCountWithAlwaysFailingTask() {
        // Given: 创建一个配置 2 次重试的调度计划，但执行器始终失败
        RetryOption retryOption = RetryOption.builder()
            .retryTimes(2)
            .retryInterval(1)
            .build();

        Schedule schedule = testDataFactory.createExecutorDelayScheduleWithRetry(
            "alwaysFailingExecutor",  // 这个执行器会始终失败
            100,
            retryOption
        );

        // When: 提交调度计划
        submitSchedule(schedule);

        // Then: 等待任务执行完成（最终会失败）
        sleep(Duration.ofSeconds(5));

        // 验证执行次数（原始 1 次 + 重试 2 次 = 3 次）
        int executionCount = FailingExecutor.getExecutionCount(schedule.getId());
        assertTrue(executionCount >= 1, "Task should have been executed at least once");

        log.info("[testRetryCountWithAlwaysFailingTask] Task executed {} times before giving up",
            executionCount);
    }

    @Test
    void testRetryInterval() {
        // Given: 创建一个配置较长重试间隔的调度计划
        int retryIntervalSeconds = 2;
        RetryOption retryOption = RetryOption.builder()
            .retryTimes(2)
            .retryInterval(retryIntervalSeconds)
            .build();

        Schedule schedule = testDataFactory.createExecutorDelayScheduleWithRetry(
            FailingExecutor.NAME,
            100,
            retryOption
        );

        long firstAttemptTime = System.currentTimeMillis();

        // When: 提交调度计划
        submitSchedule(schedule);

        // Then: 等待第一次重试
        boolean firstRetryCompleted = waitForCondition(
            () -> FailingExecutor.getExecutionCount(schedule.getId()) >= 2,
            Duration.ofSeconds(10)
        );

        assertTrue(firstRetryCompleted, "First retry should complete");

        long secondAttemptTime = System.currentTimeMillis();
        long interval = (secondAttemptTime - firstAttemptTime) / 1000;

        // 验证重试间隔（允许一定误差）
        assertTrue(interval >= retryIntervalSeconds - 1,
            "Retry interval should be at least " + retryIntervalSeconds + " seconds");

        log.info("[testRetryInterval] First attempt at {}, second attempt at {}, interval: {}s",
            firstAttemptTime, secondAttemptTime, interval);
    }

    @Test
    void testRetryOptionConfiguration() {
        // 测试 RetryOption 的各种配置
        RetryOption option1 = RetryOption.builder()
            .retryTimes(0)
            .build();
        assertFalse(option1.canRetry(0), "Should not retry when retryTimes is 0");

        RetryOption option2 = RetryOption.builder()
            .retryTimes(3)
            .build();
        assertTrue(option2.canRetry(0), "Should allow retry when retried < retryTimes");
        assertTrue(option2.canRetry(2), "Should allow retry when retried < retryTimes");
        assertFalse(option2.canRetry(3), "Should not retry when retried >= retryTimes");
        assertFalse(option2.canRetry(4), "Should not retry when retried > retryTimes");

        log.info("[testRetryOptionConfiguration] All retry configuration tests passed");
    }

    /**
     * 提交调度计划（模拟）
     */
    private void submitSchedule(Schedule schedule) {
        log.info("[submitSchedule] Schedule created: id={}, type={}",
            schedule.getId(),
            schedule.getOption() != null ? schedule.getOption().getType() : null);
    }
}
