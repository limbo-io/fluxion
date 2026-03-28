package io.fluxion.server.core.execution.fault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 错误信息
     */
    private Throwable error;

    /**
     * 任务输出（可选）
     */
    private Object output;

    /**
     * 错误分类
     */
    private ErrorCategory errorCategory;

    /**
     * 创建成功结果
     */
    public static ExecutionResult success() {
        return ExecutionResult.builder()
            .success(true)
            .build();
    }

    /**
     * 创建成功结果（带输出）
     */
    public static ExecutionResult success(Object output) {
        return ExecutionResult.builder()
            .success(true)
            .output(output)
            .build();
    }

    /**
     * 创建失败结果
     */
    public static ExecutionResult failed(Throwable error, ErrorCategory category) {
        return ExecutionResult.builder()
            .success(false)
            .error(error)
            .errorCategory(category)
            .build();
    }

    /**
     * 创建失败结果（默认 UNKNOWN 分类）
     */
    public static ExecutionResult failed(Throwable error) {
        return failed(error, ErrorCategory.UNKNOWN);
    }
}