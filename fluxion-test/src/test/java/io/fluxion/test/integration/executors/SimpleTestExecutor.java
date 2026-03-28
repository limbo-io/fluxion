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

/**
 * 简单测试执行器 - 用于验证 EXECUTOR 类型的正常执行
 *
 * @author Devil
 */
@Slf4j
@Component
public class SimpleTestExecutor implements Executor {

    public static final String NAME = "io.fluxion.test.integration.executors.SimpleTestExecutor";

    /**
     * 存储任务执行结果的静态 Map，用于测试验证
     */
    private static final Map<String, ExecutionRecord> executionRecords = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void run(TaskContext context) {
        String taskId = context.getId();
        String jobId = context.getJobId();

        log.info("[SimpleTestExecutor] Executing taskId={}, jobId={}", taskId, jobId);

        ExecutionRecord record = new ExecutionRecord();
        record.taskId = taskId;
        record.jobId = jobId;
        record.startTime = System.currentTimeMillis();

        try {
            // 模拟任务执行
            record.result = "Success";

            // 模拟执行耗时
            Thread.sleep(100);

            record.success = true;
            record.endTime = System.currentTimeMillis();

        } catch (Exception e) {
            log.error("[SimpleTestExecutor] Execution failed", e);
            record.success = false;
            record.errorMsg = e.getMessage();
            record.endTime = System.currentTimeMillis();
            throw new RuntimeException(e);
        } finally {
            executionRecords.put(taskId, record);
        }

        log.info("[SimpleTestExecutor] Completed taskId={}, success={}", taskId, record.success);
    }

    /**
     * 获取执行记录
     */
    public static ExecutionRecord getExecutionRecord(String taskId) {
        return executionRecords.get(taskId);
    }

    /**
     * 清除所有执行记录
     */
    public static void clearRecords() {
        executionRecords.clear();
    }

    /**
     * 执行记录
     */
    public static class ExecutionRecord {
        public String taskId;
        public String jobId;
        public String result;
        public boolean success;
        public String errorMsg;
        public long startTime;
        public long endTime;

        @Override
        public String toString() {
            return "ExecutionRecord{" +
                "taskId='" + taskId + '\'' +
                ", jobId='" + jobId + '\'' +
                ", success=" + success +
                ", result='" + result + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                '}';
        }
    }
}
