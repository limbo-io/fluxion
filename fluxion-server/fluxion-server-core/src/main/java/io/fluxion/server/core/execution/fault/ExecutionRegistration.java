package io.fluxion.server.core.execution.fault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 执行注册结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionRegistration {

    /**
     * 是否注册成功
     */
    private boolean registered;

    /**
     * 执行ID
     */
    private String executionId;

    /**
     * 超时时间戳
     */
    private Long timeoutTimestamp;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建失败的注册结果
     */
    public static ExecutionRegistration failed(String errorMessage) {
        return ExecutionRegistration.builder()
            .registered(false)
            .errorMessage(errorMessage)
            .build();
    }
}