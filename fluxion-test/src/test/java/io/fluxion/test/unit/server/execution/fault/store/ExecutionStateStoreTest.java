package io.fluxion.test.core.faulttolerance.store;

import io.fluxion.server.core.execution.fault.ExecutionInfo;
import io.fluxion.server.core.execution.fault.ExecutionState;
import io.fluxion.server.core.execution.fault.store.ExecutionStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionStateStoreTest {

    private ExecutionStateStore store;

    @BeforeEach
    void setUp() {
        store = new ExecutionStateStore();
    }

    @Test
    void shouldAddAndRetrieveExecution() {
        ExecutionInfo info = ExecutionInfo.builder()
            .executionId("exec-001")
            .workerId("worker-001")
            .state(ExecutionState.RUNNING)
            .build();

        store.add(info);

        ExecutionInfo retrieved = store.get("exec-001");
        assertNotNull(retrieved);
        assertEquals("exec-001", retrieved.getExecutionId());
        assertEquals("worker-001", retrieved.getWorkerId());
    }

    @Test
    void shouldReturnNullForNonExistentExecution() {
        ExecutionInfo retrieved = store.get("non-existent");
        assertNull(retrieved);
    }

    @Test
    void shouldRemoveExecution() {
        ExecutionInfo info = ExecutionInfo.builder()
            .executionId("exec-001")
            .workerId("worker-001")
            .state(ExecutionState.RUNNING)
            .build();

        store.add(info);
        store.remove("exec-001");

        assertNull(store.get("exec-001"));
    }

    @Test
    void shouldGetActiveExecutionsByWorker() {
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

        ExecutionInfo pending = ExecutionInfo.builder()
            .executionId("exec-003")
            .workerId("worker-001")
            .state(ExecutionState.PENDING)
            .build();

        store.add(running);
        store.add(succeeded);
        store.add(pending);

        List<ExecutionInfo> active = store.getActiveByWorker("worker-001");

        assertEquals(2, active.size());
        assertTrue(active.stream().anyMatch(e -> e.getExecutionId().equals("exec-001")));
        assertTrue(active.stream().anyMatch(e -> e.getExecutionId().equals("exec-003")));
    }

    @Test
    void shouldReturnEmptyListForNonExistentWorker() {
        List<ExecutionInfo> active = store.getActiveByWorker("non-existent");
        assertTrue(active.isEmpty());
    }

    @Test
    void shouldCountActiveExecutions() {
        store.add(ExecutionInfo.builder().executionId("exec-001").state(ExecutionState.RUNNING).build());
        store.add(ExecutionInfo.builder().executionId("exec-002").state(ExecutionState.PENDING).build());
        store.add(ExecutionInfo.builder().executionId("exec-003").state(ExecutionState.SUCCEEDED).build());

        assertEquals(2, store.getActiveCount());
    }
}