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

package io.fluxion.test.integration.executors;

import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.task.TaskContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 失败测试执行器 - 用于验证失败重试能力
 *
 * @author Devil
 */
@Slf4j
@Component
public class FailingExecutor implements Executor {

    public static final String NAME = "io.fluxion.test.integration.executors.FailingExecutor";

    /**
     * 每个 taskId 的执行次数计数器
     */
    private static final Map<String, AtomicInteger> executionCounters = new ConcurrentHashMap<>();

    /**
     * 执行结果记录
     */
    private static final Map<String, ExecutionResult> executionResults = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void run(TaskContext context) {
        String taskId = context.getId();
        String jobId = context.getJobId();

        // 获取或创建计数器
        AtomicInteger counter = executionCounters.computeIfAbsent(taskId, k -> new AtomicInteger(0));
        int currentAttempt = counter.incrementAndGet();

        log.info("[FailingExecutor] Executing taskId={}, jobId={}, attempt={}",
            taskId, jobId, currentAttempt);

        ExecutionResult result = new ExecutionResult();
        result.taskId = taskId;
        result.jobId = jobId;
        result.attempt = currentAttempt;
        result.startTime = System.currentTimeMillis();

        try {
            // 前 N 次执行失败
            if (currentAttempt <= 2) {
                log.warn("[FailingExecutor] Task {} failing on attempt {}", taskId, currentAttempt);
                throw new RuntimeException("Simulated failure (attempt " + currentAttempt + ")");
            }

            // 第 3 次及以后成功
            log.info("[FailingExecutor] Task {} succeeded on attempt {}",
                taskId, currentAttempt);

            result.success = true;
            result.result = "Success after " + currentAttempt + " attempts";
            result.endTime = System.currentTimeMillis();

        } catch (Exception e) {
            result.success = false;
            result.errorMsg = e.getMessage();
            result.endTime = System.currentTimeMillis();
            executionResults.put(taskId, result);
            throw e;
        }

        executionResults.put(taskId, result);
    }

    /**
     * 获取执行次数
     */
    public static int getExecutionCount(String taskId) {
        AtomicInteger counter = executionCounters.get(taskId);
        return counter != null ? counter.get() : 0;
    }

    /**
     * 获取执行结果
     */
    public static ExecutionResult getExecutionResult(String taskId) {
        return executionResults.get(taskId);
    }

    /**
     * 清除所有记录
     */
    public static void clearRecords() {
        executionCounters.clear();
        executionResults.clear();
    }

    /**
     * 执行结果
     */
    public static class ExecutionResult {
        public String taskId;
        public String jobId;
        public int attempt;
        public boolean success;
        public String result;
        public String errorMsg;
        public long startTime;
        public long endTime;

        @Override
        public String toString() {
            return "ExecutionResult{" +
                "taskId='" + taskId + '\'' +
                ", attempt=" + attempt +
                ", success=" + success +
                ", errorMsg='" + errorMsg + '\'' +
                '}';
        }
    }
}
