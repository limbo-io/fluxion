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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 计数器执行器 - 用于验证工作流中多个节点的执行顺序
 *
 * @author Devil
 */
@Slf4j
@Component
public class CounterExecutor implements Executor {

    public static final String NAME = "io.fluxion.test.integration.executors.CounterExecutor";
    public static final String NAME_A = "io.fluxion.test.integration.executors.CounterExecutorA";
    public static final String NAME_B = "io.fluxion.test.integration.executors.CounterExecutorB";

    /**
     * 每个 taskId 的执行顺序记录
     */
    private static final Map<String, List<ExecutionStep>> executionTraces = new ConcurrentHashMap<>();

    /**
     * 全局静态计数器
     */
    private static final Counter globalCounter = new Counter();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void run(TaskContext context) {
        String taskId = context.getId();
        String jobId = context.getJobId();

        int sequence = globalCounter.increment();

        log.info("[CounterExecutor] Executing taskId={}, jobId={}, sequence={}", taskId, jobId, sequence);

        ExecutionStep step = new ExecutionStep();
        step.taskId = taskId;
        step.jobId = jobId;
        step.sequence = sequence;
        step.executorName = name();
        step.timestamp = System.currentTimeMillis();

        // 获取或创建执行轨迹
        List<ExecutionStep> trace = executionTraces.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>());
        trace.add(step);

        // 模拟执行
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("[CounterExecutor] Completed taskId={}, sequence={}", taskId, sequence);
    }

    /**
     * 获取执行轨迹
     */
    public static List<ExecutionStep> getExecutionTrace(String taskId) {
        return executionTraces.get(taskId);
    }

    /**
     * 获取全局计数器值
     */
    public static int getGlobalCount() {
        return globalCounter.get();
    }

    /**
     * 重置全局计数器
     */
    public static void resetGlobalCounter() {
        globalCounter.reset();
    }

    /**
     * 清除所有记录
     */
    public static void clearRecords() {
        executionTraces.clear();
    }

    /**
     * 执行步骤记录
     */
    public static class ExecutionStep {
        public String taskId;
        public String jobId;
        public int sequence;
        public String executorName;
        public long timestamp;

        @Override
        public String toString() {
            return "ExecutionStep{" +
                "taskId='" + taskId + '\'' +
                ", jobId='" + jobId + '\'' +
                ", sequence=" + sequence +
                ", executorName='" + executorName + '\'' +
                '}';
        }
    }

    /**
     * 线程安全计数器
     */
    private static class Counter {
        private int count = 0;

        public synchronized int increment() {
            return ++count;
        }

        public synchronized int get() {
            return count;
        }

        public synchronized void reset() {
            count = 0;
        }
    }

    /**
     * 执行器 A - 用于分支测试
     */
    @Component
    public static class CounterExecutorA extends CounterExecutor {
        @Override
        public String name() {
            return NAME_A;
        }
    }

    /**
     * 执行器 B - 用于分支测试
     */
    @Component
    public static class CounterExecutorB extends CounterExecutor {
        @Override
        public String name() {
            return NAME_B;
        }
    }
}
