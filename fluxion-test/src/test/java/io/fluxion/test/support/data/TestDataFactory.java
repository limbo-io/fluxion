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

package io.fluxion.test.support.data;

import io.fluxion.server.core.executor.option.RetryOption;
import io.fluxion.server.core.schedule.Schedule;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import io.fluxion.test.support.environment.TestProfiles;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 【测试基础设施 - 测试数据工厂】
 * 
 * 作用：统一创建各类测试所需的领域对象（Schedule、RetryOption 等）
 *       保证测试数据的一致性和可追溯性
 * 
 * 特性：
 *   - 自动生成唯一 ID（带前缀便于识别）
 *   - 记录所有创建的对象，支持批量清理
 *   - 提供常用的默认配置（延迟、重试等）
 * 
 * 使用示例：
 * <pre>
 *   // 创建延迟 2 秒后执行的调度
 *   Schedule schedule = testDataFactory.createExecutorDelaySchedule("myExecutor", 2000);
 *   
 *   // 创建带 3 次重试的调度
 *   RetryOption retry = testDataFactory.createRetryOption(3);
 *   Schedule schedule = testDataFactory.createExecutorDelayScheduleWithRetry("myExecutor", 1000, retry);
 * </pre>
 * 
 * @author Devil
 * @see io.fluxion.test.support.base.BaseIntegrationTest
 */
@Slf4j
@Component
public class TestDataFactory {

    /**
     * 测试对象注册表 - 用于测试结束后清理
     * 每个测试方法执行后会调用 clearAll() 清理
     */
    private final List<String> createdScheduleIds = new ArrayList<>();
    private final List<String> createdJobIds = new ArrayList<>();
    private final List<String> createdExecutionIds = new ArrayList<>();

    // ===== EXECUTOR 类型调度 =====

    /**
     * 创建简单的延迟调度计划（EXECUTOR 类型）
     * 
     * 使用场景：简单的单次延迟执行测试
     * 
     * @param executorName 执行器名称（Spring Bean 名称）
     * @param delayMillis  延迟毫秒数（从当前时间开始计算）
     * @return Schedule 对象（未持久化，需通过 Command 发送）
     */
    public Schedule createExecutorDelaySchedule(String executorName, long delayMillis) {
        String scheduleId = generateId(TestProfiles.ID_PREFIX_SCHEDULE);

        ScheduleOption option = new ScheduleOption(
            ScheduleType.FIXED_DELAY,           // 固定延迟调度
            LocalDateTime.now().plusNanos(delayMillis * 1_000_000), // 首次触发时间
            null,                                 // 结束时间（无限制）
            Duration.ofMillis(delayMillis),       // 执行完成后延迟
            null,                                 // 固定速率（不适用）
            null,                                 // Cron 表达式（不适用）
            null                                  // Cron 类型（不适用）
        );

        Schedule schedule = new Schedule();
        schedule.setId(scheduleId);
        schedule.setOption(option);
        schedule.setEnabled(true);

        registerSchedule(scheduleId);
        log.info("[TestDataFactory] 创建 EXECUTOR 延迟调度: id={}, executor={}, delay={}ms",
            scheduleId, executorName, delayMillis);

        return schedule;
    }

    /**
     * 创建带重试配置的延迟调度计划
     * 
     * 使用场景：测试失败重试机制
     * 
     * @param executorName 执行器名称
     * @param delayMillis  延迟毫秒数
     * @param retryOption  重试配置（通过 {@link #createRetryOption} 创建）
     * @return Schedule 对象
     */
    public Schedule createExecutorDelayScheduleWithRetry(String executorName,
                                                          long delayMillis,
                                                          RetryOption retryOption) {
        String scheduleId = generateId(TestProfiles.ID_PREFIX_SCHEDULE);

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
        // 注意：RetryOption 需要设置到 Executable（Job/Workflow）上，不是 Schedule
        // 这里返回的 Schedule 需配合 Executable 的重试配置使用

        registerSchedule(scheduleId);
        log.info("[TestDataFactory] 创建 EXECUTOR 延迟调度(带重试): id={}, executor={}, delay={}ms, retry={}",
            scheduleId, executorName, delayMillis, retryOption);

        return schedule;
    }

    /**
     * 创建 Cron 表达式调度计划（EXECUTOR 类型）
     * 
     * 使用场景：定时任务测试（如每日凌晨执行）
     * 
     * @param executorName 执行器名称
     * @param cron         Cron 表达式（Quartz 格式）
     * @return Schedule 对象
     */
    public Schedule createExecutorCronSchedule(String executorName, String cron) {
        String scheduleId = generateId(TestProfiles.ID_PREFIX_SCHEDULE);

        ScheduleOption option = new ScheduleOption(
            ScheduleType.CRON,
            LocalDateTime.now(),     // 调度开始时间
            null,                    // 结束时间
            null,                    // 固定延迟（不适用）
            null,                    // 固定速率（不适用）
            cron,                    // Cron 表达式
            "QUARTZ"                 // Cron 解析器类型
        );

        Schedule schedule = new Schedule();
        schedule.setId(scheduleId);
        schedule.setOption(option);
        schedule.setEnabled(true);

        registerSchedule(scheduleId);
        log.info("[TestDataFactory] 创建 EXECUTOR Cron调度: id={}, executor={}, cron={}",
            scheduleId, executorName, cron);

        return schedule;
    }

    /**
     * 创建固定速率调度计划（Fixed Rate）
     * 
     * 使用场景：固定间隔触发，不管上次是否执行完成
     * 
     * @param executorName   执行器名称
     * @param intervalMillis 执行间隔毫秒
     * @return Schedule 对象
     */
    public Schedule createExecutorFixedRateSchedule(String executorName, long intervalMillis) {
        String scheduleId = generateId(TestProfiles.ID_PREFIX_SCHEDULE);

        ScheduleOption option = new ScheduleOption(
            ScheduleType.FIXED_RATE,
            LocalDateTime.now(),
            null,
            null,
            Duration.ofMillis(intervalMillis),  // 固定速率间隔
            null,
            null
        );

        Schedule schedule = new Schedule();
        schedule.setId(scheduleId);
        schedule.setOption(option);
        schedule.setEnabled(true);

        registerSchedule(scheduleId);
        log.info("[TestDataFactory] 创建 EXECUTOR 固定速率调度: id={}, executor={}, interval={}ms",
            scheduleId, executorName, intervalMillis);

        return schedule;
    }

    // ===== WORKFLOW 类型调度 =====

    /**
     * 创建工作流延迟调度计划
     * 
     * 使用场景：DAG 工作流测试
     * 
     * @param workflowId  工作流 ID
     * @param delayMillis 延迟毫秒数
     * @return Schedule 对象
     */
    public Schedule createWorkflowDelaySchedule(String workflowId, long delayMillis) {
        String scheduleId = generateId(TestProfiles.ID_PREFIX_SCHEDULE);

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

        registerSchedule(scheduleId);
        log.info("[TestDataFactory] 创建 WORKFLOW 延迟调度: id={}, workflowId={}, delay={}ms",
            scheduleId, workflowId, delayMillis);

        return schedule;
    }

    // ===== RetryOption 重试配置 =====

    /**
     * 创建默认重试配置
     * 
     * @param maxRetries 最大重试次数（不包括首次执行）
     * @return RetryOption
     */
    public RetryOption createRetryOption(int maxRetries) {
        return RetryOption.builder()
            .retryTimes(maxRetries)
            .retryInterval(1)     // 默认 1 秒间隔
            .build();
    }

    /**
     * 创建自定义间隔的重试配置
     * 
     * @param maxRetries     最大重试次数
     * @param intervalSeconds 每次重试间隔秒数
     * @return RetryOption
     */
    public RetryOption createRetryOption(int maxRetries, int intervalSeconds) {
        return RetryOption.builder()
            .retryTimes(maxRetries)
            .retryInterval(intervalSeconds)
            .build();
    }

    /**
     * 创建指数退避重试配置
     * 
     * @param maxRetries      最大重试次数
     * @param initialInterval 初始间隔秒数
     * @param multiplier      每次重试间隔乘数
     * @return RetryOption
     */
    public RetryOption createExponentialBackoffRetryOption(int maxRetries, 
                                                            int initialInterval,
                                                            double multiplier) {
        return RetryOption.builder()
            .retryTimes(maxRetries)
            .retryInterval(initialInterval)
            .retryBackoff(multiplier)
            .build();
    }

    // ===== 通用工具方法 =====

    /**
     * 生成唯一 ID
     * 
     * 格式: 前缀-UUID(16位大写)
     * 示例: SCH-A1B2C3D4E5F67890
     * 
     * @param prefix 前缀（建议使用 TestProfiles.ID_PREFIX_* 常量）
     * @return 唯一 ID
     */
    public String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    /**
     * 注册创建的 Schedule ID（用于后续清理）
     */
    private void registerSchedule(String scheduleId) {
        createdScheduleIds.add(scheduleId);
    }

    /**
     * 手动记录 Job ID（当通过其他方式创建 Job 时）
     */
    public void recordJobId(String jobId) {
        createdJobIds.add(jobId);
        log.debug("[TestDataFactory] 记录 Job ID: {}", jobId);
    }

    /**
     * 手动记录 Execution ID（当通过其他方式创建 Execution 时）
     */
    public void recordExecutionId(String executionId) {
        createdExecutionIds.add(executionId);
        log.debug("[TestDataFactory] 记录 Execution ID: {}", executionId);
    }

    /**
     * 清理所有记录的测试数据
     * 
     * 调用时机：
     *   - BaseIntegrationTest.tearDown() 自动调用
     *   - 手动执行测试前后清理
     */
    public void clearAll() {
        log.info("[TestDataFactory] ========== 清理测试数据 ==========");
        log.info("[TestDataFactory] Schedules 创建数: {}", createdScheduleIds.size());
        log.info("[TestDataFactory] Jobs 创建数: {}", createdJobIds.size());
        log.info("[TestDataFactory] Executions 创建数: {}", createdExecutionIds.size());

        createdScheduleIds.clear();
        createdJobIds.clear();
        createdExecutionIds.clear();

        log.info("[TestDataFactory] ========== 测试数据已清空 ==========");
    }

    // ===== 查询方法（调试使用） =====

    public List<String> getCreatedScheduleIds() {
        return new ArrayList<>(createdScheduleIds);
    }

    public List<String> getCreatedJobIds() {
        return new ArrayList<>(createdJobIds);
    }

    public List<String> getCreatedExecutionIds() {
        return new ArrayList<>(createdExecutionIds);
    }
}
