package io.fluxion.test.core.faulttolerance.failover;

import io.fluxion.server.core.execution.fault.failover.FailoverResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FailoverResultTest {

    @Test
    void shouldCreateSuccessResult() {
        FailoverResult result = FailoverResult.success("exec-001", "worker-002");

        assertTrue(result.isSuccess());
        assertEquals("exec-001", result.getExecutionId());
        assertEquals("worker-002", result.getNewWorkerId());
        assertNull(result.getErrorMessage());
    }

    @Test
    void shouldCreateFailedResult() {
        FailoverResult result = FailoverResult.failed("exec-001", "No available worker");

        assertFalse(result.isSuccess());
        assertEquals("exec-001", result.getExecutionId());
        assertEquals("No available worker", result.getErrorMessage());
    }
}