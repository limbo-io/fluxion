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

package io.fluxion.test.support.executors;

import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.task.TaskContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 【测试执行器 - 失败模拟执行器】
 * 
 * 作用：模拟任务执行失败场景，用于验证
 *       - 失败重试机制
 *       - 失败告警
 *       - 最终失败处理
 * 
 * 失败策略：
 *   - 前 2 次执行必然失败（模拟临时故障）
 *   - 第 3 次及以后执行成功（模拟故障恢复）
 *   - 每次失败抛出不同错误消息，便于追踪
 * 
 * 追踪能力：
 *   - 记录每个任务的执行次数（包括失败和成功）
 *   - 记录每次执行的详细结果
 * 
 * 使用场景：
 *   - 重试策略测试（验证重试次数、间隔）
 *   - 最终失败处理验证
 *   - 告警触发验证
 * 
 * 使用示例：
 * <pre>
 *   // 配置重试 3 次的调度
 *   RetryOption retry = testDataFactory.createRetryOption(3);
 *   Schedule schedule = ...
 *   
 *   // 等待执行完成
 *   // 预期行为：
 *   //   - 第1次：失败
 *   //   - 第2次：失败
 *   //   - 第3次：成功
 *   
 *   // 验证执行了 3 次
 *   int count = FailingExecutor.getExecutionCount(taskId);
 *   assertEquals(3, count);
 * </pre>
 * 
 * @author Devil
 * @see io.fluxion.test.support.executors.SimpleTestExecutor
 * @see io.fluxion.test.support.executors.CounterExecutor
 */
@Slf4j
@Component
public class FailingExecutor implements Executor {

    /**
     * 执行器名称
     */
    public static final String NAME = "io.fluxion.test.support.executors.FailingExecutor";

    /**
     * 执行次数计数器（每个 taskId 独立）
     * 
     * 用途：追踪每个任务实例的执行次数（包括失败和成功的尝试）
     * 原子性：使用 AtomicInteger 保证并发安全
     */
    private static final Map<String, AtomicInteger> executionCounters = new ConcurrentHashMap<>();

    /**
     * 执行结果记录
     * 
     * 保存每次执行的详细结果（包括失败的尝试）
     */
    private static final Map<String, ExecutionResult> executionResults = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return NAME;
    }

    /**
     * 执行任务（带模拟失败逻辑）
     * 
     * 执行流程：
     *   1. 递增该任务的执行计数
     *   2. 如果次数 <= 2，抛出异常（模拟失败）
     *   3. 如果次数 >= 3，执行成功
     *   4. 记录执行结果
     * 
     * 第 3 次才成功的原因：
     *   - 第1次：首次执行（失败）
     *   - 第2次：第1次重试（失败）
     *   - 第3次：第2次重试（成功）
     *   → 验证了 "重试 2 次" 的配置生效
     */
    @Override
    public void run(TaskContext context) {
        String taskId = context.getId();
        String jobId = context.getJobId();

        // 获取或创建该任务的计数器，并递增
        AtomicInteger counter = executionCounters.computeIfAbsent(taskId, k -> new AtomicInteger(0));
        int currentAttempt = counter.incrementAndGet();

        log.info("[FailingExecutor] ========== 开始执行(第 {} 次) ==========", currentAttempt);
        log.info("[FailingExecutor] taskId={}, jobId={}", taskId, jobId);

        // 创建执行结果记录
        ExecutionResult result = new ExecutionResult();
        result.taskId = taskId;
        result.jobId = jobId;
        result.attempt = currentAttempt;
        result.startTime = System.currentTimeMillis();

        try {
            // ===== 模拟前 2 次执行失败 =====
            if (currentAttempt <= 2) {
                log.warn("[FailingExecutor] ⚠️ 模拟第 {} 次执行失败", currentAttempt);
                throw new RuntimeException("模拟执行失败(第 " + currentAttempt + " 次尝试)");
            }

            // ===== 第 3 次及以后执行成功 =====
            log.info("[FailingExecutor] ✅ 第 {} 次执行成功", currentAttempt);

            result.success = true;
            result.result = "经过 " + currentAttempt + " 次尝试后成功";
            result.endTime = System.currentTimeMillis();

            log.info("[FailingExecutor] ========== 执行成功 ==========");

        } catch (Exception e) {
            // 记录失败信息
            result.success = false;
            result.errorMsg = e.getMessage();
            result.endTime = System.currentTimeMillis();
            executionResults.put(taskId, result);
            
            log.error("[FailingExecutor] ❌ 执行失败: {}", e.getMessage());
            
            // 重新抛出异常，触发重试机制
            throw e;
        }

        // 保存成功结果
        executionResults.put(taskId, result);
    }

    // ===== 静态查询方法 =====

    /**
     * 获取任务的执行次数
     * 
     * @param taskId 任务ID
     * @return 执行次数（包括失败和成功的尝试），未执行返回 0
     */
    public static int getExecutionCount(String taskId) {
        AtomicInteger counter = executionCounters.get(taskId);
        return counter != null ? counter.get() : 0;
    }

    /**
     * 获取任务的执行结果
     * 
     * @param taskId 任务ID
     * @return ExecutionResult，未找到返回 null
     */
    public static ExecutionResult getExecutionResult(String taskId) {
        return executionResults.get(taskId);
    }

    /**
     * 清除所有记录
     * 
     * 测试结束后清理，避免影响后续测试
     */
    public static void clearRecords() {
        executionCounters.clear();
        executionResults.clear();
        log.debug("[FailingExecutor] 记录已清空");
    }

    // ===== 执行结果内部类 =====

    /**
     * 执行结果记录
     * 
     * 包含单次尝试的完整信息
     */
    public static class ExecutionResult {
        public String taskId;       // 任务实例ID
        public String jobId;        // 所属 Job ID
        public int attempt;         // 第几次尝试
        public boolean success;     // 是否成功
        public String result;       // 成功结果（失败时为 null）
        public String errorMsg;     // 错误信息（成功时为 null）
        public long startTime;      // 开始时间戳
        public long endTime;        // 结束时间戳

        /**
         * 计算执行耗时
         */
        public long getDurationMillis() {
            if (endTime == 0 || startTime == 0) {
                return -1;
            }
            return endTime - startTime;
        }

        @Override
        public String toString() {
            return "ExecutionResult{" +
                "taskId='" + taskId + '\'' +
                ", attempt=" + attempt +
                ", success=" + success +
                ", errorMsg='" + errorMsg + '\'' +
                ", duration=" + getDurationMillis() + "ms" +
                '}';
        }
    }
}
