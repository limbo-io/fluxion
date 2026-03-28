package io.fluxion.test.core.faulttolerance.retry;

import io.fluxion.server.core.execution.fault.ErrorCategory;
import io.fluxion.server.core.execution.fault.ExecutionInfo;
import io.fluxion.server.core.execution.fault.retry.RetryContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RetryContextTest {

    @Test
    void shouldCreateRetryContext() {
        ExecutionInfo info = ExecutionInfo.builder()
            .executionId("exec-001")
            .retryCount(2)
            .build();

        RuntimeException error = new RuntimeException("Test error");
        RetryContext context = RetryContext.builder()
            .executionInfo(info)
            .error(error)
            .errorCategory(ErrorCategory.TRANSIENT)
            .build();

        assertEquals("exec-001", context.getExecutionInfo().getExecutionId());
        assertEquals(2, context.getExecutionInfo().getRetryCount());
        assertEquals(ErrorCategory.TRANSIENT, context.getErrorCategory());
    }
}