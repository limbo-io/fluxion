package io.fluxion.test.core.faulttolerance;

import io.fluxion.server.core.execution.fault.DefaultFaultToleranceCoordinator;
import io.fluxion.server.core.execution.fault.ExecutionInfo;
import io.fluxion.server.core.execution.fault.ExecutionState;
import io.fluxion.server.core.execution.fault.config.FaultToleranceProperties;
import io.fluxion.server.core.execution.fault.failover.DefaultFailoverManager;
import io.fluxion.server.core.execution.fault.retry.RetryContext;
import io.fluxion.server.core.execution.fault.retry.RetryStrategy;
import io.fluxion.server.core.execution.fault.store.ExecutionStateStore;
import io.fluxion.server.core.execution.fault.timeout.TimeoutCallback;
import io.fluxion.server.core.execution.fault.timeout.TimeoutManager;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultFaultToleranceCoordinatorTest {

    @Test
    void shouldScheduleRetryAfterTimeout() {
        ExecutionStateStore store = new ExecutionStateStore();
        CapturingTimeoutManager timeoutManager = new CapturingTimeoutManager();
        CapturingCoordinator coordinator = newCoordinator(store, timeoutManager);

        coordinator.register(ExecutionInfo.builder()
            .executionId("execution-timeout")
            .jobId("job-timeout")
            .state(ExecutionState.RUNNING)
            .build());
        timeoutManager.fire("execution-timeout");

        ExecutionInfo execution = store.get("execution-timeout");
        assertEquals(ExecutionState.RETRYING, execution.getState());
        assertEquals(1, execution.getRetryCount());
        assertEquals("execution-timeout", coordinator.scheduledExecutionId);
        assertEquals(Duration.ofSeconds(1), coordinator.scheduledDelay);
    }

    @Test
    void shouldCountWorkerOfflineAsRetry() {
        ExecutionStateStore store = new ExecutionStateStore();
        CapturingCoordinator coordinator = newCoordinator(store, new CapturingTimeoutManager());
        store.add(ExecutionInfo.builder()
            .executionId("execution-offline")
            .jobId("job-offline")
            .workerId("worker-offline")
            .state(ExecutionState.RUNNING)
            .build());

        coordinator.onWorkerOffline("worker-offline");

        ExecutionInfo execution = store.get("execution-offline");
        assertEquals(ExecutionState.RETRYING, execution.getState());
        assertEquals(1, execution.getRetryCount());
        assertEquals("execution-offline", coordinator.scheduledExecutionId);
    }

    private CapturingCoordinator newCoordinator(ExecutionStateStore store, TimeoutManager timeoutManager) {
        RetryStrategy retryStrategy = new RetryStrategy() {
            @Override
            public Duration nextRetryDelay(RetryContext context) {
                return Duration.ofSeconds(1);
            }

            @Override
            public boolean shouldRetry(RetryContext context, Throwable error) {
                return true;
            }
        };
        return new CapturingCoordinator(store, retryStrategy, timeoutManager);
    }

    private static class CapturingCoordinator extends DefaultFaultToleranceCoordinator {

        private String scheduledExecutionId;
        private Duration scheduledDelay;

        private CapturingCoordinator(ExecutionStateStore store, RetryStrategy retryStrategy, TimeoutManager timeoutManager) {
            super(store, retryStrategy, timeoutManager, new DefaultFailoverManager(store), new FaultToleranceProperties());
        }

        @Override
        protected void scheduleRetry(String executionId, Duration delay) {
            scheduledExecutionId = executionId;
            scheduledDelay = delay;
        }
    }

    private static class CapturingTimeoutManager implements TimeoutManager {

        private final Map<String, TimeoutCallback> callbacks = new HashMap<>();

        @Override
        public void addTimeout(String executionId, Duration timeout, TimeoutCallback callback) {
            callbacks.put(executionId, callback);
        }

        @Override
        public void cancelTimeout(String executionId) {
            callbacks.remove(executionId);
        }

        private void fire(String executionId) {
            callbacks.get(executionId).onTimeout(executionId);
        }
    }
}
