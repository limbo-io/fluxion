package io.fluxion.test.unit.server.execution.fault.timeout;

import io.fluxion.server.core.execution.fault.DefaultFaultToleranceCoordinator;
import io.fluxion.server.core.execution.fault.ErrorCategory;
import io.fluxion.server.core.execution.fault.ExecutionInfo;
import io.fluxion.server.core.execution.fault.ExecutionRegistration;
import io.fluxion.server.core.execution.fault.ExecutionResult;
import io.fluxion.server.core.execution.fault.ExecutionState;
import io.fluxion.server.core.execution.fault.config.FaultToleranceProperties;
import io.fluxion.server.core.execution.fault.failover.DefaultFailoverManager;
import io.fluxion.server.core.execution.fault.failover.FailoverManager;
import io.fluxion.server.core.execution.fault.retry.ExponentialBackoffRetryStrategy;
import io.fluxion.server.core.execution.fault.retry.RetryStrategy;
import io.fluxion.server.core.execution.fault.store.ExecutionStateStore;
import io.fluxion.server.core.execution.fault.timeout.TimingWheelTimeoutManager;
import io.fluxion.server.core.execution.fault.timeout.TimeoutCallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TimingWheelTimeoutManagerTest {

    private TimingWheelTimeoutManager manager;
    private ExecutionStateStore store;
    private RetryStrategy retryStrategy;
    private FailoverManager failoverManager;
    private DefaultFaultToleranceCoordinator coordinator;

    @BeforeEach
    void setUp() {
        manager = new TimingWheelTimeoutManager();
        store = new ExecutionStateStore();
        retryStrategy = new ExponentialBackoffRetryStrategy(
            5, Duration.ofMillis(10), Duration.ofMillis(100), 2.0, 0.1
        );
        failoverManager = new DefaultFailoverManager(store);
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

    @Test
    void shouldUseConfiguredTimeoutValue() {
        // Configure 50ms timeout for fast test configuration
        FaultToleranceProperties properties = new FaultToleranceProperties();
        FaultToleranceProperties.TimeoutConfig timeoutConfig = new FaultToleranceProperties.TimeoutConfig();
        timeoutConfig.setDefaultTimeout(Duration.ofMillis(50));
        properties.setTimeout(timeoutConfig);

        coordinator = new DefaultFaultToleranceCoordinator(
            store, retryStrategy, manager, failoverManager, properties
        );

        ExecutionInfo info = ExecutionInfo.builder()
            .executionId("exec-001")
            .jobId("job-001")
            .workerId("worker-001")
            .state(ExecutionState.RUNNING)
            .build();

        ExecutionRegistration reg = coordinator.register(info);

        assertTrue(reg.isRegistered());
        // Verify timeout is set (50ms from current time)
        long expectedMinTimeout = System.currentTimeMillis() - 100; // Allow some tolerance
        assertTrue(reg.getTimeoutTimestamp() > expectedMinTimeout);
        assertTrue(reg.getTimeoutTimestamp() <= System.currentTimeMillis() + 200);
    }

    @Test
    void shouldTransitionToRetryingOnTimeout() throws InterruptedException {
        // Configure 50ms timeout and retry for transient errors
        FaultToleranceProperties properties = new FaultToleranceProperties();
        FaultToleranceProperties.TimeoutConfig timeoutConfig = new FaultToleranceProperties.TimeoutConfig();
        timeoutConfig.setDefaultTimeout(Duration.ofMillis(50));
        properties.setTimeout(timeoutConfig);

        coordinator = new DefaultFaultToleranceCoordinator(
            store, retryStrategy, manager, failoverManager, properties
        );

        ExecutionInfo info = ExecutionInfo.builder()
            .executionId("exec-timeout-001")
            .jobId("job-001")
            .workerId("worker-001")
            .state(ExecutionState.RUNNING)
            .retryCount(0)
            .build();

        coordinator.register(info);

        // Wait for timeout to trigger
        Thread.sleep(150);

        // Execution should be in RETRYING state because timeout is treated as TRANSIENT error
        ExecutionInfo stored = store.get("exec-timeout-001");
        assertNotNull(stored);
        assertEquals(ExecutionState.RETRYING, stored.getState());
        assertEquals(1, stored.getRetryCount());
        assertTrue(stored.getEndTime() > 0);
    }

    @Test
    void shouldUseDefaultTimeoutWhenPropertiesNull() {
        // Create coordinator with null properties (should fall back to default 30min)
        coordinator = new DefaultFaultToleranceCoordinator(
            store, retryStrategy, manager, failoverManager, null
        );

        ExecutionInfo info = ExecutionInfo.builder()
            .executionId("exec-002")
            .jobId("job-002")
            .workerId("worker-002")
            .state(ExecutionState.RUNNING)
            .build();

        ExecutionRegistration reg = coordinator.register(info);

        assertTrue(reg.isRegistered());
        // Default timeout is 30 minutes, so timestamp should be ~30 min from now
        long expectedMinTimeout = System.currentTimeMillis() + Duration.ofMinutes(29).toMillis();
        long expectedMaxTimeout = System.currentTimeMillis() + Duration.ofMinutes(31).toMillis();
        assertTrue(reg.getTimeoutTimestamp() >= expectedMinTimeout, 
            "Timeout should be at least 29 minutes from now");
        assertTrue(reg.getTimeoutTimestamp() <= expectedMaxTimeout,
            "Timeout should be at most 31 minutes from now");
    }

    @Test
    void shouldApplyRetryPolicyToTimeout() throws InterruptedException {
        // Configure 50ms timeout and only 1 retry allowed
        FaultToleranceProperties properties = new FaultToleranceProperties();
        FaultToleranceProperties.TimeoutConfig timeoutConfig = new FaultToleranceProperties.TimeoutConfig();
        timeoutConfig.setDefaultTimeout(Duration.ofMillis(50));
        properties.setTimeout(timeoutConfig);
        properties.getRetry().setMaxRetries(1);

        coordinator = new DefaultFaultToleranceCoordinator(
            store, retryStrategy, manager, failoverManager, properties
        );

        ExecutionInfo info = ExecutionInfo.builder()
            .executionId("exec-timeout-002")
            .jobId("job-001")
            .workerId("worker-001")
            .state(ExecutionState.RUNNING)
            .retryCount(0)
            .build();

        coordinator.register(info);

        // Wait for first timeout
        Thread.sleep(150);

        // Should be in RETRYING state after first timeout
        ExecutionInfo stored = store.get("exec-timeout-002");
        assertNotNull(stored);
        assertEquals(ExecutionState.RETRYING, stored.getState());
        assertEquals(1, stored.getRetryCount());
    }
}