package io.fluxion.test.unit.server.execution.fault;

import io.fluxion.server.core.execution.fault.ExecutionRegistration;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionRegistrationTest {

    @Test
    void shouldCreateRegistration() {
        long now = System.currentTimeMillis();
        ExecutionRegistration reg = ExecutionRegistration.builder()
            .executionId("exec-001")
            .timeoutTimestamp(now + Duration.ofMinutes(30).toMillis())
            .registered(true)
            .build();

        assertEquals("exec-001", reg.getExecutionId());
        assertTrue(reg.isRegistered());
        assertNotNull(reg.getTimeoutTimestamp());
    }

    @Test
    void shouldCreateFailedRegistration() {
        ExecutionRegistration reg = ExecutionRegistration.failed("Already registered");

        assertFalse(reg.isRegistered());
        assertEquals("Already registered", reg.getErrorMessage());
    }
}