package io.fluxion.server.core.execution.fault.retry;

import java.time.Duration;

/**
 * 重试策略接口
 */
public interface RetryStrategy {

    /**
     * 计算下次重试时间
     * @param context 重试上下文
     * @return 延迟时间，null 表示不再重试
     */
    Duration nextRetryDelay(RetryContext context);

    /**
     * 是否应该重试
     * @param context 重试上下文
     * @param error 错误
     * @return 是否应该重试
     */
    boolean shouldRetry(RetryContext context, Throwable error);
}