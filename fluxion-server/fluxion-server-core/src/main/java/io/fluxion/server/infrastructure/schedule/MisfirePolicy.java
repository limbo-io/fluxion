package io.fluxion.server.infrastructure.schedule;

/**
 * Determines how an overdue PENDING Execution is handled.
 */
public enum MisfirePolicy {
    SKIP,
    FIRE_RETRY
}
