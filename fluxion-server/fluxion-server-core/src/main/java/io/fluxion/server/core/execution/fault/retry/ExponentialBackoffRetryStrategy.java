package io.fluxion.server.core.execution.fault.retry;

import io.fluxion.server.core.execution.fault.ErrorCategory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 指数退避重试策略
 */
@Slf4j
@Getter
public class ExponentialBackoffRetryStrategy implements RetryStrategy {

    /**
     * 最大重试次数
     */
    private final int maxRetries;

    /**
     * 初始延迟
     */
    private final Duration initialDelay;

    /**
     * 最大延迟
     */
    private final Duration maxDelay;

    /**
     * 乘数因子
     */
    private final double multiplier;

    /**
     * 随机抖动因子 (0.0 - 1.0)
     */
    private final double jitter;

    public ExponentialBackoffRetryStrategy(int maxRetries, Duration initialDelay,
                                           Duration maxDelay, double multiplier, double jitter) {
        this.maxRetries = maxRetries;
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
        this.multiplier = multiplier;
        this.jitter = Math.min(1.0, Math.max(0.0, jitter));
    }

    @Override
    public Duration nextRetryDelay(RetryContext context) {
        if (!shouldRetry(context, context.getError())) {
            return null;
        }

        int retryCount = context.getExecutionInfo().getRetryCount();

        // 计算指数退避延迟
        long delayMs = (long) (initialDelay.toMillis() * Math.pow(multiplier, retryCount));

        // 限制在最大延迟内
        delayMs = Math.min(delayMs, maxDelay.toMillis());

        // 添加随机抖动（避免惊群效应）
        if (jitter > 0) {
            double jitterFactor = 1.0 - jitter + (2 * jitter * ThreadLocalRandom.current().nextDouble());
            delayMs = (long) (delayMs * jitterFactor);
        }

        log.debug("[FAULT-RETRY] Calculated delay: {}ms for executionId={}, retryCount={}",
            delayMs, context.getExecutionInfo().getExecutionId(), retryCount);

        return Duration.ofMillis(delayMs);
    }

    @Override
    public boolean shouldRetry(RetryContext context, Throwable error) {
        // 检查重试次数
        if (context.getExecutionInfo().getRetryCount() >= maxRetries) {
            return false;
        }

        // 检查错误分类
        ErrorCategory category = context.getErrorCategory();
        if (category == null) {
            category = ErrorCategory.UNKNOWN;
        }

        return category.isRetryable();
    }
}