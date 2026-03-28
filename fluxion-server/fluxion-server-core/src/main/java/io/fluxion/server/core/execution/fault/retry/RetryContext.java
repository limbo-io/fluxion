package io.fluxion.server.core.execution.fault.retry;

import io.fluxion.server.core.execution.fault.ErrorCategory;
import io.fluxion.server.core.execution.fault.ExecutionInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 重试上下文
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryContext {

    /**
     * 执行信息
     */
    private ExecutionInfo executionInfo;

    /**
     * 错误
     */
    private Throwable error;

    /**
     * 错误分类
     */
    private ErrorCategory errorCategory;
}