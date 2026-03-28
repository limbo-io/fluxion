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

import io.fluxion.server.core.execution.ExecutableType;
import io.fluxion.server.core.executor.option.RetryOption;
import io.fluxion.server.core.schedule.Schedule;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 测试数据工厂
 * 用于创建各种类型的测试数据
 *
 * @author Devil
 */
@Slf4j
@Component
public class TestDataFactory {

    /**
     * 存储创建的测试数据，便于清理
     */
    private final List<String> createdScheduleIds = new ArrayList<>();
    private final List<String> createdJobIds = new ArrayList<>();
    private final List<String> createdExecutionIds = new ArrayList<>();

    /**
     * 创建简单的延迟调度计划
     *
     * @param executorName 执行器名称
     * @param delayMillis  延迟毫秒数
     * @return Schedule
     */
    public Schedule createExecutorDelaySchedule(String executorName, long delayMillis) {
        String scheduleId = generateId("SCH");

        ScheduleOption option = new ScheduleOption(
            ScheduleType.FIXED_DELAY,
            LocalDateTime.now().plusNanos(delayMillis * 1_000_000),
            null,
            Duration.ofMillis(delayMillis),
            null,
            null,
            null
        );

        Schedule schedule = new Schedule();
        schedule.setId(scheduleId);
        schedule.setOption(option);
        schedule.setEnabled(true);

        createdScheduleIds.add(scheduleId);
        log.info("[TestDataFactory] Created EXECUTOR delay schedule: id={}, executor={}, delay={}ms",
            scheduleId, executorName, delayMillis);

        return schedule;
    }

    /**
     * 创建带重试配置的延迟调度计划
     *
     * @param executorName 执行器名称
     * @param delayMillis  延迟毫秒数
     * @param retryOption  重试配置
     * @return Schedule
     */
    public Schedule createExecutorDelayScheduleWithRetry(String executorName,
                                                          long delayMillis,
                                                          RetryOption retryOption) {
        String scheduleId = generateId("SCH");

        ScheduleOption option = new ScheduleOption(
            ScheduleType.FIXED_DELAY,
            LocalDateTime.now().plusNanos(delayMillis * 1_000_000),
            null,
            Duration.ofMillis(delayMillis),
            null,
            null,
            null
        );

        Schedule schedule = new Schedule();
        schedule.setId(scheduleId);
        schedule.setOption(option);
        schedule.setEnabled(true);

        createdScheduleIds.add(scheduleId);
        log.info("[TestDataFactory] Created EXECUTOR delay schedule with retry: id={}, executor={}, delay={}ms, retry={}",
            scheduleId, executorName, delayMillis, retryOption);

        return schedule;
    }

    /**
     * 创建 Cron 调度计划 - EXECUTOR 类型
     *
     * @param executorName 执行器名称
     * @param cron         Cron 表达式
     * @return Schedule
     */
    public Schedule createExecutorCronSchedule(String executorName, String cron) {
        String scheduleId = generateId("SCH");

        ScheduleOption option = new ScheduleOption(
            ScheduleType.CRON,
            LocalDateTime.now(),
            null,
            null,
            null,
            cron,
            "QUARTZ"
        );

        Schedule schedule = new Schedule();
        schedule.setId(scheduleId);
        schedule.setOption(option);
        schedule.setEnabled(true);

        createdScheduleIds.add(scheduleId);
        log.info("[TestDataFactory] Created EXECUTOR cron schedule: id={}, executor={}, cron={}",
            scheduleId, executorName, cron);

        return schedule;
    }

    /**
     * 创建工作流调度计划
     *
     * @param workflowId 工作流 ID
     * @param delayMillis 延迟毫秒数
     * @return Schedule
     */
    public Schedule createWorkflowDelaySchedule(String workflowId, long delayMillis) {
        String scheduleId = generateId("SCH");

        ScheduleOption option = new ScheduleOption(
            ScheduleType.FIXED_DELAY,
            LocalDateTime.now().plusNanos(delayMillis * 1_000_000),
            null,
            Duration.ofMillis(delayMillis),
            null,
            null,
            null
        );

        Schedule schedule = new Schedule();
        schedule.setId(scheduleId);
        schedule.setOption(option);
        schedule.setEnabled(true);

        createdScheduleIds.add(scheduleId);
        log.info("[TestDataFactory] Created WORKFLOW delay schedule: id={}, workflowId={}, delay={}ms",
            scheduleId, workflowId, delayMillis);

        return schedule;
    }

    /**
     * 创建默认重试配置
     *
     * @param maxRetries 最大重试次数
     * @return RetryOption
     */
    public RetryOption createRetryOption(int maxRetries) {
        return RetryOption.builder()
            .retryTimes(maxRetries)
            .retryInterval(1) // 1 秒间隔
            .build();
    }

    /**
     * 创建默认重试配置（带自定义间隔）
     *
     * @param maxRetries     最大重试次数
     * @param intervalSeconds 重试间隔秒数
     * @return RetryOption
     */
    public RetryOption createRetryOption(int maxRetries, int intervalSeconds) {
        return RetryOption.builder()
            .retryTimes(maxRetries)
            .retryInterval(intervalSeconds)
            .build();
    }

    /**
     * 生成唯一 ID
     */
    public String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    /**
     * 记录创建的 Job ID
     */
    public void recordJobId(String jobId) {
        createdJobIds.add(jobId);
    }

    /**
     * 记录创建的 Execution ID
     */
    public void recordExecutionId(String executionId) {
        createdExecutionIds.add(executionId);
    }

    /**
     * 清理所有测试数据
     */
    public void clearAll() {
        log.info("[TestDataFactory] Clearing all test data...");
        log.info("[TestDataFactory] Schedules created: {}", createdScheduleIds.size());
        log.info("[TestDataFactory] Jobs created: {}", createdJobIds.size());
        log.info("[TestDataFactory] Executions created: {}", createdExecutionIds.size());

        createdScheduleIds.clear();
        createdJobIds.clear();
        createdExecutionIds.clear();

        log.info("[TestDataFactory] All test data cleared");
    }

    /**
     * 获取创建的 Schedule IDs
     */
    public List<String> getCreatedScheduleIds() {
        return new ArrayList<>(createdScheduleIds);
    }

    /**
     * 获取创建的 Job IDs
     */
    public List<String> getCreatedJobIds() {
        return new ArrayList<>(createdJobIds);
    }

    /**
     * 获取创建的 Execution IDs
     */
    public List<String> getCreatedExecutionIds() {
        return new ArrayList<>(createdExecutionIds);
    }
}
