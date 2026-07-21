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

package io.fluxion.test.integration.executor;

import io.fluxion.server.core.schedule.Schedule;
import io.fluxion.server.core.schedule.cmd.ScheduleSaveCmd;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;
import io.fluxion.test.support.base.BaseIntegrationTest;
import io.fluxion.test.support.executors.SimpleTestExecutor;
import io.limbo.cqrs.spring.command.Cmd;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 【集成测试 - 执行器调度执行链路】
 * 
 * 测试目标：验证 EXECUTOR 类型任务从提交到执行的完整链路
 * 
 * 覆盖场景：
 *   - 简单执行器任务的提交和执行
 *   - 多个调度计划的并发执行
 *   - 调度时间和执行时间的准确性验证
 * 
 * 执行链路测试：
 *   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
 *   │ Create      │───▶│ Submit      │───▶│ Schedule    │───▶│ Worker      │
 *   │ Schedule    │    │ (CQRS Cmd)  │    │ Engine      │    │ Execute     │
 *   └─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
 *          │                  │                  │                  │
 *          ▼                  ▼                  ▼                  ▼
 *   TestDataFactory      Cmd.send()        TimingWheel      SimpleTestExecutor
 *   (createExecutor    (ScheduleSaveCmd)  (计算触发时间)      (run method)
 *    DelaySchedule)
 * 
 * @author Devil
 * @see io.fluxion.test.support.executors.SimpleTestExecutor
 */
@Slf4j
public class ExecutorIntegrationTest extends BaseIntegrationTest {

    /**
     * 测试简单执行器任务的完整生命周期
     * 
     * 验证点：
     *   1. 调度计划正确提交
     *   2. Worker 接收到任务
     *   3. 执行器正确执行
     *   4. 执行记录正确保存
     */
    @Test
    void testSimpleExecutorJob() {
        // ===== Given: 创建一个延迟 500ms 的 EXECUTOR 类型调度计划 =====
        Schedule schedule = testDataFactory.createExecutorDelaySchedule(
            SimpleTestExecutor.NAME,
            500
        );

        // ===== When: 提交调度计划 =====
        log.info("[testSimpleExecutorJob] 提交调度计划: id={}", schedule.getId());
        submitSchedule(schedule);

        // ===== Then: 等待任务执行并验证结果 =====
        boolean executed = waitForCondition(
            () -> SimpleTestExecutor.getExecutionRecord(schedule.getId()) != null,
            DEFAULT_TIMEOUT
        );

        assertTrue(executed, "简单执行器应该被执行");

        SimpleTestExecutor.ExecutionRecord record =
            SimpleTestExecutor.getExecutionRecord(schedule.getId());

        // 验证执行记录的各个字段
        assertAll("执行记录验证",
            () -> assertNotNull(record, "执行记录应该存在"),
            () -> assertTrue(record.success, "执行应该成功"),
            () -> assertEquals("Success", record.result, "结果应该是 'Success'"),
            () -> assertNotNull(record.taskId, "Task ID 不应该为空"),
            () -> assertNotNull(record.jobId, "Job ID 不应该为空"),
            () -> assertTrue(record.getDurationMillis() >= 100, 
                "执行耗时应至少 100ms (SimpleTestExecutor 模拟耗时)")
        );

        log.info("[testSimpleExecutorJob] 执行完成: {}", record);
    }

    /**
     * 测试多个调度计划的并发执行
     * 
     * 验证点：
     *   1. 多个 Schedule 可并发提交
     *   2. 调度引擎正确处理多个触发时间
     *   3. 所有任务都被执行到
     *   4. 无任务丢失
     */
    @Test
    void testExecutorJobWithMultipleSchedules() {
        // ===== Given: 创建 3 个不同延迟的调度计划 =====
        Schedule schedule1 = testDataFactory.createExecutorDelaySchedule(
            SimpleTestExecutor.NAME, 100);  // 100ms 延迟
        Schedule schedule2 = testDataFactory.createExecutorDelaySchedule(
            SimpleTestExecutor.NAME, 200);  // 200ms 延迟
        Schedule schedule3 = testDataFactory.createExecutorDelaySchedule(
            SimpleTestExecutor.NAME, 300);  // 300ms 延迟

        // ===== When: 提交所有调度计划 =====
        log.info("[testExecutorJobWithMultipleSchedules] 提交 3 个调度计划");
        submitSchedule(schedule1);
        submitSchedule(schedule2);
        submitSchedule(schedule3);

        // ===== Then: 等待所有任务执行完成 =====
        boolean allExecuted = waitForCondition(
            () -> SimpleTestExecutor.getExecutionRecord(schedule1.getId()) != null
                && SimpleTestExecutor.getExecutionRecord(schedule2.getId()) != null
                && SimpleTestExecutor.getExecutionRecord(schedule3.getId()) != null,
            DEFAULT_TIMEOUT
        );

        assertTrue(allExecuted, "所有执行器都应该被执行");

        // 获取各执行记录
        SimpleTestExecutor.ExecutionRecord record1 =
            SimpleTestExecutor.getExecutionRecord(schedule1.getId());
        SimpleTestExecutor.ExecutionRecord record2 =
            SimpleTestExecutor.getExecutionRecord(schedule2.getId());
        SimpleTestExecutor.ExecutionRecord record3 =
            SimpleTestExecutor.getExecutionRecord(schedule3.getId());

        // 验证所有记录都存在且成功
        assertAll("多调度执行验证",
            () -> assertNotNull(record1, "调度1应该有执行记录"),
            () -> assertNotNull(record2, "调度2应该有执行记录"),
            () -> assertNotNull(record3, "调度3应该有执行记录"),
            () -> assertTrue(record1.success, "调度1应该执行成功"),
            () -> assertTrue(record2.success, "调度2应该执行成功"),
            () -> assertTrue(record3.success, "调度3应该执行成功")
        );

        log.info("[testExecutorJobWithMultipleSchedules] 全部 {} 个调度执行成功",
            testDataFactory.getCreatedScheduleIds().size());
    }

    /**
     * 测试执行时间精度
     * 
     * 验证点：
     *   1. 调度时间准确性（实际触发时间 >= 计划延迟时间）
     *   2. 执行耗时准确性
     */
    @Test
    void testExecutorJobExecutionTime() {
        // ===== Given: 创建一个延迟 100ms 的调度计划 =====
        long delayMs = 100;
        Schedule schedule = testDataFactory.createExecutorDelaySchedule(
            SimpleTestExecutor.NAME,
            delayMs
        );

        long submitTime = System.currentTimeMillis();

        // ===== When: 提交调度计划 =====
        log.info("[testExecutorJobExecutionTime] 提交调度: 期望延迟 {}ms", delayMs);
        submitSchedule(schedule);

        // ===== Then: 验证执行时间 =====
        boolean executed = waitForCondition(
            () -> SimpleTestExecutor.getExecutionRecord(schedule.getId()) != null,
            SHORT_TIMEOUT
        );

        assertTrue(executed, "执行器应该被执行");

        SimpleTestExecutor.ExecutionRecord record =
            SimpleTestExecutor.getExecutionRecord(schedule.getId());

        assertNotNull(record, "应该有执行记录");
        
        // 计算调度等待时间（提交到实际开始执行）
        long waitTime = record.startTime - submitTime;
        
        // 验证调度时间至少接近预期延迟（允许 50ms 误差）
        assertTrue(waitTime >= delayMs - 50,
            String.format("实际等待时间(%dms)应该至少接近预期延迟(%dms)", waitTime, delayMs));

        // 验证执行耗时（SimpleTestExecutor 内部休眠 100ms）
        long runTime = record.getDurationMillis();
        assertTrue(runTime >= 100, 
            String.format("执行耗时(%dms)应该至少 100ms", runTime));

        log.info("[testExecutorJobExecutionTime] 期望延迟: {}ms, 实际等待: {}ms, 执行耗时: {}ms",
            delayMs, waitTime, runTime);
    }

    /**
     * 提交调度计划（内部工具方法）
     * 
     * 使用 CQRS 命令发送 ScheduleSaveCmd 到 Broker
     * 
     * @param schedule Schedule 对象（需已设置 option）
     * @throws IllegalArgumentException 如果 option 为 null
     */
    private void submitSchedule(Schedule schedule) {
        ScheduleOption option = schedule.getOption();
        if (option == null) {
            throw new IllegalArgumentException("Schedule option 不能为空");
        }

        // 使用 Builder 构建保存命令
        ScheduleSaveCmd cmd = ScheduleSaveCmd.builder()
            .id(schedule.getId())
            .option(option)
            .build();

        // 通过 CQRS 框架发送命令
        Cmd.send(cmd);

        log.info("[submitSchedule] 调度已提交: id={}, type={}, triggerAt={}",
            schedule.getId(),
            option.getType(),
            option.getStartTime());
    }
}
