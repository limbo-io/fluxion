package io.fluxion.test.core.faulttolerance;

import io.fluxion.server.core.execution.fault.ErrorCategory;
import io.fluxion.server.core.execution.fault.ExecutionResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionResultTest {

    @Test
    void shouldCreateSuccessResult() {
        ExecutionResult result = ExecutionResult.success();

        assertTrue(result.isSuccess());
        assertNull(result.getError());
        assertNull(result.getErrorCategory());
    }

    @Test
    void shouldCreateSuccessResultWithOutput() {
        ExecutionResult result = ExecutionResult.success("output-data");

        assertTrue(result.isSuccess());
        assertEquals("output-data", result.getOutput());
    }

    @Test
    void shouldCreateFailedResult() {
        RuntimeException error = new RuntimeException("Network timeout");
        ExecutionResult result = ExecutionResult.failed(error, ErrorCategory.TRANSIENT);

        assertFalse(result.isSuccess());
        assertEquals("Network timeout", result.getError().getMessage());
        assertEquals(ErrorCategory.TRANSIENT, result.getErrorCategory());
        assertTrue(result.getErrorCategory().isRetryable());
    }

    @Test
    void shouldCreateFailedResultWithUnknownCategory() {
        RuntimeException error = new RuntimeException("Unknown error");
        ExecutionResult result = ExecutionResult.failed(error);

        assertFalse(result.isSuccess());
        assertEquals(ErrorCategory.UNKNOWN, result.getErrorCategory());
    }
}