package io.fluxion.test.core.faulttolerance.retry;

import io.fluxion.server.core.execution.fault.ErrorCategory;
import io.fluxion.server.core.execution.fault.ExecutionInfo;
import io.fluxion.server.core.execution.fault.retry.ExponentialBackoffRetryStrategy;
import io.fluxion.server.core.execution.fault.retry.RetryContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ExponentialBackoffRetryStrategyTest {

    private ExponentialBackoffRetryStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new ExponentialBackoffRetryStrategy(
            5,                          // maxRetries
            Duration.ofSeconds(1),      // initialDelay
            Duration.ofSeconds(30),     // maxDelay
            2.0,                        // multiplier
            0.1                         // jitter
        );
    }

    @Test
    void shouldCalculateExponentialDelay() {
        ExecutionInfo info1 = ExecutionInfo.builder().executionId("exec-001").retryCount(0).build();
        RetryContext ctx1 = RetryContext.builder().executionInfo(info1).errorCategory(ErrorCategory.TRANSIENT).build();
        Duration delay1 = strategy.nextRetryDelay(ctx1);
        // 1s * 2^0 = 1s, with jitter (0.9s - 1.1s)
        assertTrue(delay1.toMillis() >= 900 && delay1.toMillis() <= 1100);

        ExecutionInfo info2 = ExecutionInfo.builder().executionId("exec-002").retryCount(2).build();
        RetryContext ctx2 = RetryContext.builder().executionInfo(info2).errorCategory(ErrorCategory.TRANSIENT).build();
        Duration delay2 = strategy.nextRetryDelay(ctx2);
        // 1s * 2^2 = 4s, with jitter (3.6s - 4.4s)
        assertTrue(delay2.toMillis() >= 3600 && delay2.toMillis() <= 4400);
    }

    @Test
    void shouldCapAtMaxDelay() {
        // Use retryCount=4 (under max of 5) but delay would be 1s * 2^4 = 16s
        // Test with higher multiplier scenario where delay exceeds max
        ExponentialBackoffRetryStrategy cappedStrategy = new ExponentialBackoffRetryStrategy(
            5,                          // maxRetries
            Duration.ofSeconds(10),     // initialDelay (10s * 2^4 = 160s, exceeds 30s max)
            Duration.ofSeconds(30),     // maxDelay
            2.0,                        // multiplier
            0.0                         // no jitter for predictable test
        );

        ExecutionInfo info = ExecutionInfo.builder().executionId("exec-001").retryCount(4).build();
        RetryContext ctx = RetryContext.builder().executionInfo(info).errorCategory(ErrorCategory.TRANSIENT).build();
        Duration delay = cappedStrategy.nextRetryDelay(ctx);
        // Should cap at 30s
        assertTrue(delay.toMillis() <= 30000); // max delay
    }

    @Test
    void shouldReturnNullWhenMaxRetriesReached() {
        ExecutionInfo info = ExecutionInfo.builder().executionId("exec-001").retryCount(5).build();
        RetryContext ctx = RetryContext.builder().executionInfo(info).errorCategory(ErrorCategory.TRANSIENT).build();
        Duration delay = strategy.nextRetryDelay(ctx);
        assertNull(delay);
    }

    @Test
    void shouldNotRetryForNonRetryableError() {
        ExecutionInfo info = ExecutionInfo.builder().executionId("exec-001").retryCount(0).build();
        RetryContext ctx = RetryContext.builder()
            .executionInfo(info)
            .errorCategory(ErrorCategory.BUSINESS)
            .build();
        Duration delay = strategy.nextRetryDelay(ctx);
        assertNull(delay);
    }

    @Test
    void shouldDetermineIfShouldRetry() {
        ExecutionInfo info = ExecutionInfo.builder().executionId("exec-001").retryCount(2).build();
        RuntimeException error = new RuntimeException("Test error");

        RetryContext ctx = RetryContext.builder()
            .executionInfo(info)
            .errorCategory(ErrorCategory.TRANSIENT)
            .build();

        assertTrue(strategy.shouldRetry(ctx, error));

        // Max retries reached
        ExecutionInfo maxInfo = ExecutionInfo.builder().executionId("exec-002").retryCount(5).build();
        RetryContext maxCtx = RetryContext.builder()
            .executionInfo(maxInfo)
            .errorCategory(ErrorCategory.TRANSIENT)
            .build();

        assertFalse(strategy.shouldRetry(maxCtx, error));

        // Non-retryable error category
        RetryContext bizCtx = RetryContext.builder()
            .executionInfo(info)
            .errorCategory(ErrorCategory.BUSINESS)
            .build();
        assertFalse(strategy.shouldRetry(bizCtx, error));
    }
}