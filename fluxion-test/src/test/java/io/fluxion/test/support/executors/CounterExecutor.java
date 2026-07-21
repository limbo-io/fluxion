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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 【测试执行器 - 计数器执行器】
 * 
 * 作用：用于验证工作流中多个节点的执行顺序和次数
 *       提供全局计数器追踪执行序列
 * 
 * 核心功能：
 *   - 每个执行记录带全局递增序列号
 *   - 支持同一任务的多次执行轨迹追踪
 *   - 内置 A/B 两个变体执行器，用于分支测试
 * 
 * 使用场景：
 *   - DAG 工作流执行顺序验证（确保节点按正确拓扑序执行）
 *   - 广播任务验证（多个 Worker 同时执行）
 *   - 并发执行验证
 * 
 * 使用示例：
 * <pre>
 *   // 验证执行顺序
 *   List<ExecutionStep> trace = CounterExecutor.getExecutionTrace(taskId);
 *   assertEquals(3, trace.size());  // 应该执行3次
 *   
 *   // 验证全局计数
 *   assertTrue(CounterExecutor.getGlobalCount() >= 3);
 * </pre>
 * 
 * @author Devil
 * @see io.fluxion.test.support.executors.SimpleTestExecutor
 * @see io.fluxion.test.support.executors.FailingExecutor
 */
@Slf4j
@Component
public class CounterExecutor implements Executor {

    /** 主执行器名称 */
    public static final String NAME = "io.fluxion.test.support.executors.CounterExecutor";
    
    /** 变体A - 用于分支测试 */
    public static final String NAME_A = "io.fluxion.test.support.executors.CounterExecutorA";
    
    /** 变体B - 用于分支测试 */
    public static final String NAME_B = "io.fluxion.test.support.executors.CounterExecutorB";

    /**
     * 任务执行轨迹表
     * key=taskId, value=该任务的所有执行步骤（按执行顺序）
     */
    private static final Map<String, List<ExecutionStep>> executionTraces = new ConcurrentHashMap<>();

    /**
     * 全局计数器（所有任务共享，线程安全）
     * 
     * 用途：
     *   - 验证执行顺序（序列号应严格递增）
     *   - 统计总执行次数
     */
    private static final Counter globalCounter = new Counter();

    @Override
    public String name() {
        return NAME;
    }

    /**
     * 执行任务
     * 
     * 执行逻辑：
     *   1. 获取全局递增序列号
     *   2. 创建执行步骤记录
     *   3. 添加到任务的执行轨迹中
     *   4. 模拟 50ms 执行耗时
     */
    @Override
    public void run(TaskContext context) {
        String taskId = context.getId();
        String jobId = context.getJobId();

        // 获取全局序列号（原子递增）
        int sequence = globalCounter.increment();

        log.info("[CounterExecutor] 执行开始: taskId={}, jobId={}, sequence={}", 
            taskId, jobId, sequence);

        // 创建执行步骤
        ExecutionStep step = new ExecutionStep();
        step.taskId = taskId;
        step.jobId = jobId;
        step.sequence = sequence;           // 全局序列号
        step.executorName = name();         // 执行器名称
        step.timestamp = System.currentTimeMillis();

        // 添加到轨迹（如果不存在则创建新列表）
        executionTraces.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>()).add(step);

        // 模拟执行耗时
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("[CounterExecutor] 执行完成: taskId={}, sequence={}", taskId, sequence);
    }

    // ===== 静态查询方法 =====

    /**
     * 获取任务的执行轨迹
     * 
     * @param taskId 任务ID
     * @return 执行步骤列表（按执行顺序），未找到返回 null
     */
    public static List<ExecutionStep> getExecutionTrace(String taskId) {
        return executionTraces.get(taskId);
    }

    /**
     * 获取全局计数器当前值
     * @return 总执行次数
     */
    public static int getGlobalCount() {
        return globalCounter.get();
    }

    /**
     * 重置全局计数器
     * 
     * 用途：测试开始时清零，确保统计数据准确
     */
    public static void resetGlobalCounter() {
        globalCounter.reset();
        log.debug("[CounterExecutor] 全局计数器已重置");
    }

    /**
     * 清除所有执行记录
     */
    public static void clearRecords() {
        executionTraces.clear();
        log.debug("[CounterExecutor] 执行轨迹已清空");
    }

    // ===== 内部类 =====

    /**
     * 执行步骤记录
     * 
     * 记录单次执行的关键信息，用于验证执行顺序
     */
    public static class ExecutionStep {
        public String taskId;       // 任务实例ID
        public String jobId;        // 所属 Job ID
        public int sequence;        // 全局序列号（严格递增）
        public String executorName; // 执行器名称
        public long timestamp;      // 执行时间戳

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
     * 
     * 使用 synchronized 保证多 Worker 并发执行时序列号的唯一性
     */
    private static class Counter {
        private int count = 0;

        /**
         * 原子递增并返回新值
         */
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

    // ===== 变体执行器（分支测试用） =====

    /**
     * 计数器执行器 A
     * 
     * 用途：工作流条件分支测试（当条件X时执行此执行器）
     */
    @Component
    public static class CounterExecutorA extends CounterExecutor {
        @Override
        public String name() {
            return NAME_A;
        }
    }

    /**
     * 计数器执行器 B
     * 
     * 用途：工作流条件分支测试（当条件Y时执行此执行器）
     */
    @Component
    public static class CounterExecutorB extends CounterExecutor {
        @Override
        public String name() {
            return NAME_B;
        }
    }
}
