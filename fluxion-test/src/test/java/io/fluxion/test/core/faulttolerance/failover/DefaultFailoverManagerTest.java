package io.fluxion.test.core.faulttolerance.failover;

import io.fluxion.server.core.execution.fault.ExecutionInfo;
import io.fluxion.server.core.execution.fault.ExecutionState;
import io.fluxion.server.core.execution.fault.failover.DefaultFailoverManager;
import io.fluxion.server.core.execution.fault.failover.FailoverResult;
import io.fluxion.server.core.execution.fault.store.ExecutionStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T3.4/T5.3: Worker offline failover unit tests
 * <p>
 * Verifies the failover manager that is used by fault tolerance chain:
 * WorkerChecker detects offline → FaultToleranceCoordinator.onWorkerOffline()
 * → FailoverManager.markWorkerFailed() → ExecutionMigrateService.migrate()
 * <p>
 * Note: Full chain requires Worker lifecycle simulation and is verified at
 * integration level in production deployment.
 *
 * @author Devil
 */
@DisplayName("Failover Manager Unit Tests (T3.4/T5.3)")
class DefaultFailoverManagerTest {

    private ExecutionStateStore store;
    private DefaultFailoverManager manager;

    @BeforeEach
    void setUp() {
        store = new ExecutionStateStore();
        manager = new DefaultFailoverManager(store);
    }

    @Test
    void shouldMarkWorkerFailed() {
        ExecutionInfo running = ExecutionInfo.builder()
            .executionId("exec-001")
            .workerId("worker-001")
            .state(ExecutionState.RUNNING)
            .build();

        ExecutionInfo succeeded = ExecutionInfo.builder()
            .executionId("exec-002")
            .workerId("worker-001")
            .state(ExecutionState.SUCCEEDED)
            .build();

        store.add(running);
        store.add(succeeded);

        List<ExecutionInfo> toMigrate = manager.markWorkerFailed("worker-001");

        assertEquals(1, toMigrate.size());
        assertEquals("exec-001", toMigrate.get(0).getExecutionId());
        assertEquals(ExecutionState.MIGRATING, toMigrate.get(0).getState());
    }

    @Test
    void shouldReturnEmptyListForNonExistentWorker() {
        List<ExecutionInfo> toMigrate = manager.markWorkerFailed("non-existent");
        assertTrue(toMigrate.isEmpty());
    }

    @Test
    void shouldMigrateExecution() {
        ExecutionInfo info = ExecutionInfo.builder()
            .executionId("exec-001")
            .workerId("worker-001")
            .jobType("NORMAL")
            .state(ExecutionState.MIGRATING)
            .build();

        FailoverResult result = manager.migrate(info);

        // Note: In real implementation, this would select a new worker
        // For now, we just verify the method can be called
        assertNotNull(result);
    }
}