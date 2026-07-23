package io.fluxion.test.core.faulttolerance.timeout;

import io.fluxion.server.core.execution.fault.timeout.TimingWheelTimeoutManager;
import io.fluxion.server.core.execution.fault.timeout.TimeoutCallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T3.3/T5.4: Timeout mechanism unit tests
 * <p>
 * Verifies the timing wheel timeout manager that is used by:
 * - DefaultFaultToleranceCoordinator.onExecutionTimeout
 * - ExecutionRecoveryService recovery timeout tracking
 * <p>
 * Production chain (integration level verification):
 * TimeoutManager.addTimeout() → (time passes) → TimeoutCallback →
 * DefaultFaultToleranceCoordinator.onExecutionTimeout() → handleFailure() →
 * retryStrategy.shouldRetry() → scheduleRetry()
 * <p>
 * Note: Full chain with retry trigger requires integration test with real
 * timeout firing and FaultToleranceCoordinator integration.
 *
 * @author Devil
 */
@DisplayName("Timeout Manager Unit Tests (T3.3/T5.4)")
class TimingWheelTimeoutManagerTest {

    private TimingWheelTimeoutManager manager;

    @BeforeEach
    void setUp() {
        manager = new TimingWheelTimeoutManager();
    }

    @Test
    void shouldTriggerTimeout() throws InterruptedException {
        AtomicBoolean triggered = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        TimeoutCallback callback = executionId -> {
            triggered.set(true);
            latch.countDown();
        };

        manager.addTimeout("exec-001", Duration.ofMillis(100), callback);

        // Wait for timeout
        boolean completed = latch.await(500, TimeUnit.MILLISECONDS);
        assertTrue(completed, "Timeout should be triggered within 500ms");
        assertTrue(triggered.get());
    }

    @Test
    void shouldCancelTimeout() throws InterruptedException {
        AtomicBoolean triggered = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        TimeoutCallback callback = executionId -> {
            triggered.set(true);
            latch.countDown();
        };

        manager.addTimeout("exec-001", Duration.ofMillis(200), callback);
        manager.cancelTimeout("exec-001");

        // Wait longer than original timeout
        boolean completed = latch.await(400, TimeUnit.MILLISECONDS);
        assertFalse(completed, "Timeout should not be triggered after cancellation");
        assertFalse(triggered.get());
    }

    @Test
    void shouldHandleMultipleTimeouts() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(3);

        manager.addTimeout("exec-001", Duration.ofMillis(50), id -> latch.countDown());
        manager.addTimeout("exec-002", Duration.ofMillis(100), id -> latch.countDown());
        manager.addTimeout("exec-003", Duration.ofMillis(150), id -> latch.countDown());

        boolean completed = latch.await(500, TimeUnit.MILLISECONDS);
        assertTrue(completed, "All timeouts should be triggered");
    }
}