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

import io.fluxion.server.core.schedule.Schedule;
import io.fluxion.server.core.schedule.cmd.ScheduleSaveCmd;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;
import io.fluxion.test.integration.executors.SimpleTestExecutor;
import io.limbo.cqrs.spring.command.Cmd;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EXECUTOR 类型任务集成测试
 * 验证简单执行器任务的提交、调度和执行流程
 *
 * @author Devil
 */
@Slf4j
public class ExecutorIntegrationTest extends BaseIntegrationTest {

    @Test
    void testSimpleExecutorJob() {
        // Given: 创建一个延迟 500ms 的 EXECUTOR 类型调度计划
        Schedule schedule = testDataFactory.createExecutorDelaySchedule(
            SimpleTestExecutor.NAME,
            500
        );

        // When: 提交调度计划
        submitSchedule(schedule);

        // Then: 等待任务执行并验证结果
        boolean executed = waitForCondition(
            () -> SimpleTestExecutor.getExecutionRecord(schedule.getId()) != null,
            DEFAULT_TIMEOUT
        );

        assertTrue(executed, "SimpleExecutor should have been executed");

        SimpleTestExecutor.ExecutionRecord record =
            SimpleTestExecutor.getExecutionRecord(schedule.getId());

        assertNotNull(record, "Execution record should exist");
        assertTrue(record.success, "Execution should be successful");
        assertEquals("Success", record.result, "Result should be 'Success'");
        assertNotNull(record.taskId, "Task ID should not be null");
        assertNotNull(record.jobId, "Job ID should not be null");

        log.info("[testSimpleExecutorJob] Execution completed: {}", record);
    }

    @Test
    void testExecutorJobWithMultipleSchedules() {
        // Given: 创建多个调度计划
        Schedule schedule1 = testDataFactory.createExecutorDelaySchedule(
            SimpleTestExecutor.NAME, 100);
        Schedule schedule2 = testDataFactory.createExecutorDelaySchedule(
            SimpleTestExecutor.NAME, 200);
        Schedule schedule3 = testDataFactory.createExecutorDelaySchedule(
            SimpleTestExecutor.NAME, 300);

        // When: 提交所有调度计划
        submitSchedule(schedule1);
        submitSchedule(schedule2);
        submitSchedule(schedule3);

        // Then: 等待所有任务执行完成
        boolean allExecuted = waitForCondition(
            () -> SimpleTestExecutor.getExecutionRecord(schedule1.getId()) != null
                && SimpleTestExecutor.getExecutionRecord(schedule2.getId()) != null
                && SimpleTestExecutor.getExecutionRecord(schedule3.getId()) != null,
            DEFAULT_TIMEOUT
        );

        assertTrue(allExecuted, "All executors should have been executed");

        // 验证每个记录
        SimpleTestExecutor.ExecutionRecord record1 =
            SimpleTestExecutor.getExecutionRecord(schedule1.getId());
        SimpleTestExecutor.ExecutionRecord record2 =
            SimpleTestExecutor.getExecutionRecord(schedule2.getId());
        SimpleTestExecutor.ExecutionRecord record3 =
            SimpleTestExecutor.getExecutionRecord(schedule3.getId());

        assertAll(
            () -> assertNotNull(record1),
            () -> assertNotNull(record2),
            () -> assertNotNull(record3),
            () -> assertTrue(record1.success),
            () -> assertTrue(record2.success),
            () -> assertTrue(record3.success)
        );

        log.info("[testExecutorJobWithMultipleSchedules] All {} executions completed successfully",
            testDataFactory.getCreatedScheduleIds().size());
    }

    @Test
    void testExecutorJobExecutionTime() {
        // Given: 创建一个延迟 100ms 的调度计划
        long delayMs = 100;
        Schedule schedule = testDataFactory.createExecutorDelaySchedule(
            SimpleTestExecutor.NAME,
            delayMs
        );

        long submitTime = System.currentTimeMillis();

        // When: 提交调度计划
        submitSchedule(schedule);

        // Then: 验证执行时间在预期范围内
        boolean executed = waitForCondition(
            () -> SimpleTestExecutor.getExecutionRecord(schedule.getId()) != null,
            SHORT_TIMEOUT
        );

        assertTrue(executed, "Executor should have been executed");

        SimpleTestExecutor.ExecutionRecord record =
            SimpleTestExecutor.getExecutionRecord(schedule.getId());

        assertNotNull(record);
        long executionTime = record.startTime - submitTime;

        // 验证执行时间至少大于等于延迟时间（允许一定误差）
        assertTrue(executionTime >= delayMs - 50,
            "Execution should start after delay");

        // 验证执行耗时合理（SimpleTestExecutor 休眠 100ms）
        long runTime = record.endTime - record.startTime;
        assertTrue(runTime >= 100, "Execution runtime should be at least 100ms");

        log.info("[testExecutorJobExecutionTime] Delay: {}ms, actual wait: {}ms, runtime: {}ms",
            delayMs, executionTime, runTime);
    }

    /**
     * 提交调度计划
     * 使用 CQRS 命令发送 ScheduleSaveCmd
     */
    private void submitSchedule(Schedule schedule) {
        ScheduleOption option = schedule.getOption();
        if (option == null) {
            throw new IllegalArgumentException("Schedule option cannot be null");
        }

        // 使用 Builder 构建保存命令
        ScheduleSaveCmd cmd = ScheduleSaveCmd.builder()
            .id(schedule.getId())
            .option(option)
            .build();

        // 发送 CQRS 命令
        Cmd.send(cmd);

        log.info("[submitSchedule] Schedule submitted: id={}, type={}, triggerAt={}",
            schedule.getId(),
            option.getType(),
            option.getStartTime());
    }
}
