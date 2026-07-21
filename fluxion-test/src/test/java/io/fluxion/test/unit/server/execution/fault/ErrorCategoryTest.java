package io.fluxion.test.unit.server.execution.fault;

import io.fluxion.server.core.execution.fault.ErrorCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ErrorCategoryTest {

    @Test
    void shouldIdentifyRetryableCategories() {
        assertTrue(ErrorCategory.TRANSIENT.isRetryable());
        assertTrue(ErrorCategory.UNKNOWN.isRetryable());

        assertFalse(ErrorCategory.BUSINESS.isRetryable());
        assertFalse(ErrorCategory.FATAL.isRetryable());
    }
}