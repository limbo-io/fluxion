package io.fluxion.server.core.execution.fault.failover;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 故障迁移结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailoverResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 执行ID
     */
    private String executionId;

    /**
     * 新的 Worker ID
     */
    private String newWorkerId;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建成功结果
     */
    public static FailoverResult success(String executionId, String newWorkerId) {
        return FailoverResult.builder()
            .success(true)
            .executionId(executionId)
            .newWorkerId(newWorkerId)
            .build();
    }

    /**
     * 创建失败结果
     */
    public static FailoverResult failed(String executionId, String errorMessage) {
        return FailoverResult.builder()
            .success(false)
            .executionId(executionId)
            .errorMessage(errorMessage)
            .build();
    }
}