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

/**
 * 【测试执行器 - 简单测试执行器】
 * 
 * 作用：最基础的测试执行器，用于验证 EXECUTOR 类型任务的正常执行流程
 * 
 * 功能：
 *   - 记录每次执行的详细信息（开始/结束时间、结果、异常）
 *   - 延迟 100ms 模拟真实任务执行时间
 *   - 通过静态 Map 存储记录，便于测试断言
 * 
 * 使用场景：
 *   - 调度链路端到端测试（验证任务是否被正确下发和执行）
 *   - 调度时间精度测试
 *   - 简单成功场景验证
 * 
 * 使用示例：
 * <pre>
 *   // 创建引用此执行器的 Schedule
 *   Schedule schedule = testDataFactory.createExecutorDelaySchedule(
 *       SimpleTestExecutor.NAME, 1000);
 *   
 *   // 等待执行完成后验证
 *   SimpleTestExecutor.ExecutionRecord record = 
 *       SimpleTestExecutor.getExecutionRecord(taskId);
 *   assertTrue(record.success);
 * </pre>
 * 
 * @author Devil
 * @see io.fluxion.test.support.executors.CounterExecutor
 * @see io.fluxion.test.support.executors.FailingExecutor
 */
@Slf4j
@Component
public class SimpleTestExecutor implements Executor {

    /**
     * 执行器名称（用于触发器配置中引用）
     * 格式：全限定类名
     */
    public static final String NAME = "io.fluxion.test.support.executors.SimpleTestExecutor";

    /**
     * 执行记录存储（静态变量，跨测试方法共享）
     * 
     * ⚠️ 注意：
     *   - 使用 ConcurrentHashMap 保证线程安全（多 Worker 并发执行）
     *   - 测试结束后需调用 clearRecords() 清理，避免影响其他测试
     *   - key=taskId, value=执行记录
     */
    private static final Map<String, ExecutionRecord> executionRecords = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return NAME;
    }

    /**
     * 执行任务
     * 
     * 执行流程：
     *   1. 创建执行记录
     *   2. 记录开始时间
     *   3. 执行业务逻辑（模拟 100ms）
     *   4. 记录成功状态和结束时间
     *   5. 保存记录到静态 Map
     * 
     * @param context 任务上下文（包含 taskId, jobId, 参数等）
     */
    @Override
    public void run(TaskContext context) {
        String taskId = context.getId();
        String jobId = context.getJobId();

        log.info("[SimpleTestExecutor] ========== 开始执行 ==========");
        log.info("[SimpleTestExecutor] taskId={}, jobId={}", taskId, jobId);

        // 创建执行记录
        ExecutionRecord record = new ExecutionRecord();
        record.taskId = taskId;
        record.jobId = jobId;
        record.startTime = System.currentTimeMillis();

        try {
            // ===== 模拟业务逻辑执行 =====
            // 模拟耗时操作
            Thread.sleep(100);
            
            // 设置执行结果
            record.result = "执行成功";
            record.success = true;
            record.endTime = System.currentTimeMillis();

            log.info("[SimpleTestExecutor] ========== 执行成功 ==========");
            log.info("[SimpleTestExecutor] 耗时: {}ms", record.endTime - record.startTime);

        } catch (Exception e) {
            log.error("[SimpleTestExecutor] 执行异常", e);
            record.success = false;
            record.errorMsg = e.getMessage();
            record.endTime = System.currentTimeMillis();
            throw new RuntimeException(e);
        } finally {
            // 确保记录被保存（即使异常也保存失败状态）
            executionRecords.put(taskId, record);
        }
    }

    // ===== 静态查询方法（测试断言使用） =====

    /**
     * 获取指定任务的执行记录
     * 
     * @param taskId 任务ID
     * @return ExecutionRecord，未找到返回 null
     */
    public static ExecutionRecord getExecutionRecord(String taskId) {
        return executionRecords.get(taskId);
    }

    /**
     * 清除所有执行记录
     * 
     * 调用时机：测试 tearDown() 时，通过 EmbeddedFluxionEnvironment.clearData() 触发
     */
    public static void clearRecords() {
        executionRecords.clear();
        log.debug("[SimpleTestExecutor] 执行记录已清空");
    }

    /**
     * 获取当前记录数量（调试用）
     */
    public static int getRecordCount() {
        return executionRecords.size();
    }

    // ===== 执行记录内部类 =====

    /**
     * 任务执行记录
     * 
     * 包含一次任务执行的完整信息，用于测试断言验证
     */
    public static class ExecutionRecord {
        public String taskId;       // 任务实例ID
        public String jobId;        // 所属 Job ID
        public String result;       // 执行结果（成功时为字符串，失败时为null）
        public boolean success;     // 是否成功
        public String errorMsg;     // 错误信息（失败时）
        public long startTime;      // 开始时间戳（毫秒）
        public long endTime;        // 结束时间戳（毫秒）

        /**
         * 计算执行耗时
         * @return 耗时毫秒数（未结束返回 -1）
         */
        public long getDurationMillis() {
            if (endTime == 0 || startTime == 0) {
                return -1;
            }
            return endTime - startTime;
        }

        @Override
        public String toString() {
            return "ExecutionRecord{" +
                "taskId='" + taskId + '\'' +
                ", jobId='" + jobId + '\'' +
                ", success=" + success +
                ", result='" + result + '\'' +
                ", errorMsg='" + errorMsg + '\'' +
                ", duration=" + getDurationMillis() + "ms" +
                '}';
        }
    }
}
