package io.fluxion.server.core.execution.fault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 执行信息 - 任务执行的完整状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionInfo {

    /**
     * 执行ID
     */
    private String executionId;

    /**
     * 任务ID
     */
    private String jobId;

    /**
     * 调度实例ID
     */
    private String taskId;

    /**
     * 执行 Worker ID
     */
    private String workerId;

    /**
     * 任务类型
     */
    private String jobType;

    /**
     * 当前状态
     */
    private ExecutionState state;

    /**
     * 已重试次数
     */
    private int retryCount;

    /**
     * 最后一次错误
     */
    private Throwable lastError;

    /**
     * 开始时间
     */
    private long startTime;

    /**
     * 结束时间（可空）
     */
    private Long endTime;

    /**
     * 超时时间戳
     */
    private long timeoutTimestamp;

    /**
     * 执行上下文
     */
    private Map<String, String> context;

    /**
     * 增加重试计数
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }
}