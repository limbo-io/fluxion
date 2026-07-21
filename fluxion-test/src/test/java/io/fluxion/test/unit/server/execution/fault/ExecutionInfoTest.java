package io.fluxion.test.unit.server.execution.fault;

import io.fluxion.server.core.execution.fault.ExecutionInfo;
import io.fluxion.server.core.execution.fault.ExecutionState;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionInfoTest {

    @Test
    void shouldCreateExecutionInfo() {
        Map<String, String> context = new HashMap<>();
        context.put("key", "value");

        ExecutionInfo info = ExecutionInfo.builder()
            .executionId("exec-001")
            .jobId("job-001")
            .taskId("task-001")
            .workerId("worker-001")
            .jobType("NORMAL")
            .state(ExecutionState.PENDING)
            .retryCount(0)
            .startTime(System.currentTimeMillis())
            .timeoutTimestamp(System.currentTimeMillis() + Duration.ofMinutes(30).toMillis())
            .context(context)
            .build();

        assertEquals("exec-001", info.getExecutionId());
        assertEquals("job-001", info.getJobId());
        assertEquals(ExecutionState.PENDING, info.getState());
        assertTrue(info.getState().isActive());
        assertEquals(0, info.getRetryCount());
    }

    @Test
    void shouldUpdateState() {
        ExecutionInfo info = ExecutionInfo.builder()
            .executionId("exec-001")
            .state(ExecutionState.PENDING)
            .build();

        info.setState(ExecutionState.RUNNING);
        assertEquals(ExecutionState.RUNNING, info.getState());

        info.setState(ExecutionState.SUCCEEDED);
        assertTrue(info.getState().isTerminal());
    }

    @Test
    void shouldIncrementRetryCount() {
        ExecutionInfo info = ExecutionInfo.builder()
            .executionId("exec-001")
            .retryCount(0)
            .build();

        info.incrementRetryCount();
        assertEquals(1, info.getRetryCount());

        info.incrementRetryCount();
        assertEquals(2, info.getRetryCount());
    }
}