package io.fluxion.test.unit.server.execution.fault;

import io.fluxion.server.core.execution.fault.ExecutionState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionStateTest {

    @Test
    void shouldIdentifyActiveStates() {
        assertTrue(ExecutionState.PENDING.isActive());
        assertTrue(ExecutionState.DISPATCHED.isActive());
        assertTrue(ExecutionState.RUNNING.isActive());
        assertTrue(ExecutionState.RETRYING.isActive());
        assertTrue(ExecutionState.MIGRATING.isActive());

        assertFalse(ExecutionState.SUCCEEDED.isActive());
        assertFalse(ExecutionState.FAILED.isActive());
        assertFalse(ExecutionState.TIMEOUT.isActive());
        assertFalse(ExecutionState.CANCELLED.isActive());
    }

    @Test
    void shouldIdentifyTerminalStates() {
        assertTrue(ExecutionState.SUCCEEDED.isTerminal());
        assertTrue(ExecutionState.FAILED.isTerminal());
        assertTrue(ExecutionState.TIMEOUT.isTerminal());
        assertTrue(ExecutionState.CANCELLED.isTerminal());

        assertFalse(ExecutionState.PENDING.isTerminal());
        assertFalse(ExecutionState.RUNNING.isTerminal());
    }
}