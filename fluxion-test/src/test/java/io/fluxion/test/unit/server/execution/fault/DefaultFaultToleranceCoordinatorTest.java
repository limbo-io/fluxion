package io.fluxion.test.unit.server.execution.fault;

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
import io.fluxion.server.core.execution.fault.timeout.TimeoutManager;
import io.fluxion.test.integration.TestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
class DefaultFaultToleranceCoordinatorTest {

    private ExecutionStateStore store;
    private RetryStrategy retryStrategy;
    private TimeoutManager timeoutManager;
    private FailoverManager failoverManager;
    private DefaultFaultToleranceCoordinator coordinator;

    @BeforeEach
    void setUp() {
        store = new ExecutionStateStore();
        retryStrategy = new ExponentialBackoffRetryStrategy(
            5, Duration.ofSeconds(1), Duration.ofSeconds(30), 2.0, 0.1
        );
        timeoutManager = new TimingWheelTimeoutManager();
        failoverManager = new DefaultFailoverManager(store);

        // Configure test-specific timeout (50ms for fast tests)
        FaultToleranceProperties properties = new FaultToleranceProperties();
        FaultToleranceProperties.TimeoutConfig timeoutConfig = new FaultToleranceProperties.TimeoutConfig();
        timeoutConfig.setDefaultTimeout(Duration.ofMillis(50));
        properties.setTimeout(timeoutConfig);

        coordinator = new DefaultFaultToleranceCoordinator(
            store, retryStrategy, timeoutManager, failoverManager, properties
        );
    }

    @Test
    void shouldRegisterExecution() {
        ExecutionInfo info = ExecutionInfo.builder()
            .executionId("exec-001")
            .jobId("job-001")
            .workerId("worker-001")
            .state(ExecutionState.RUNNING)
            .build();

        ExecutionRegistration reg = coordinator.register(info);

        assertTrue(reg.isRegistered());
        assertEquals("exec-001", reg.getExecutionId());

        ExecutionInfo stored = store.get("exec-001");
        assertNotNull(stored);
        assertEquals(ExecutionState.RUNNING, stored.getState());
    }

    @Test
    void shouldCompleteExecutionSuccessfully() {
        ExecutionInfo info = ExecutionInfo.builder()
            .executionId("exec-001")
            .workerId("worker-001")
            .state(ExecutionState.RUNNING)
            .build();

        coordinator.register(info);
        coordinator.complete("exec-001", ExecutionResult.success());

        ExecutionInfo stored = store.get("exec-001");
        assertEquals(ExecutionState.SUCCEEDED, stored.getState());
        assertNotNull(stored.getEndTime());
    }

    @Test
    void shouldCompleteExecutionWithTransientError() {
        ExecutionInfo info = ExecutionInfo.builder()
            .executionId("exec-001")
            .workerId("worker-001")
            .state(ExecutionState.RUNNING)
            .retryCount(0)
            .build();

        coordinator.register(info);

        ExecutionResult result = ExecutionResult.failed(
            new RuntimeException("Network error"),
            ErrorCategory.TRANSIENT
        );
        coordinator.complete("exec-001", result);

        ExecutionInfo stored = store.get("exec-001");
        // Should be in RETRYING state for transient errors
        assertEquals(ExecutionState.RETRYING, stored.getState());
        assertEquals(1, stored.getRetryCount());
    }

    @Test
    void shouldCompleteExecutionWithBusinessError() {
        ExecutionInfo info = ExecutionInfo.builder()
            .executionId("exec-001")
            .workerId("worker-001")
            .state(ExecutionState.RUNNING)
            .retryCount(0)
            .build();

        coordinator.register(info);

        ExecutionResult result = ExecutionResult.failed(
            new RuntimeException("Business error"),
            ErrorCategory.BUSINESS
        );
        coordinator.complete("exec-001", result);

        ExecutionInfo stored = store.get("exec-001");
        // Business errors should not trigger retry
        assertEquals(ExecutionState.FAILED, stored.getState());
    }

    @Test
    void shouldHandleWorkerOffline() {
        ExecutionInfo info1 = ExecutionInfo.builder()
            .executionId("exec-001")
            .workerId("worker-001")
            .state(ExecutionState.RUNNING)
            .build();

        ExecutionInfo info2 = ExecutionInfo.builder()
            .executionId("exec-002")
            .workerId("worker-001")
            .state(ExecutionState.RUNNING)
            .build();

        coordinator.register(info1);
        coordinator.register(info2);

        coordinator.onWorkerOffline("worker-001");

        ExecutionInfo stored1 = store.get("exec-001");
        ExecutionInfo stored2 = store.get("exec-002");

        assertEquals(ExecutionState.MIGRATING, stored1.getState());
        assertEquals(ExecutionState.MIGRATING, stored2.getState());
    }
}