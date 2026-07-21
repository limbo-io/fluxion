/*
 * Copyright 2025-2030 fluxion-io Team (https://github.com/fluxion-io).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxion.server.core.schedule;

import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.infrastructure.dao.entity.ScheduleDelayEntity;
import io.fluxion.server.infrastructure.dao.tx.TransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import java.time.LocalDateTime;

/**
 * Maintains broker leases for schedule delays.
 * <p>
 * Lease mechanism ensures only one broker can schedule a delay at a time:
 * - Brokers claim delays via atomic UPDATE with lease_owner = NULL OR lease_until < now
 * - Brokers renew leases every 10 seconds while they own the delay
 * - Expired leases (lease_until passed) can be reclaimed by any broker after ~15 seconds
 * - Max takeover wait: ~20 seconds (15s lease + 5s scan interval typical)
 *
 * @author Devil
 */
@Slf4j
@Component
public class ScheduleLeaseMaintainer {
    @Resource
    private EntityManager entityManager;

    @Resource
    private TransactionService transactionService;

    @Resource
    private ScheduleLeaseProperties properties;

    /**
     * Atomically claim a delay for the current broker using conditional update.
     * <p>
     * The update succeeds only if:
     * - Status is INIT (not yet claimed)
     * - No one else owns it, OR lease has expired
     *
     * @param scheduleId the schedule ID
     * @param triggerAt the trigger time
     * @return true if claim succeeded, false otherwise
     */
    public boolean tryClaim(String scheduleId, LocalDateTime triggerAt) {
        String brokerId = getCurrentBrokerId();
        if (brokerId == null) {
            return false;
        }

        return transactionService.transactional(() -> {
            int updated = entityManager.createNativeQuery(
                    "UPDATE schedule_delay " +
                    "SET lease_owner = :brokerId, " +
                    "    lease_until = DATE_ADD(NOW(3), INTERVAL :duration SECOND), " +
                    "    status = :newStatus, attempt = attempt + 1, updated_at = NOW(3) " +
                    "WHERE schedule_id = :scheduleId " +
                    "AND trigger_at = :triggerAt " +
                    "AND status = :initStatus " +
                    "AND deleted = false"
                )
                .setParameter("brokerId", brokerId)
                .setParameter("duration", properties.getDuration())
                .setParameter("newStatus", ScheduleDelay.Status.CLAIMED.value)
                .setParameter("scheduleId", scheduleId)
                .setParameter("triggerAt", triggerAt)
                .setParameter("initStatus", ScheduleDelay.Status.INIT.value)
                .executeUpdate();

            return updated > 0;
        });
    }

    /**
     * Verify that the current broker still owns the lease.
     * <p>
     * This is the fencing check: before executing, confirm we still have the lease.
     * Uses MySQL NOW(3) for consistent time comparison across all database operations.
     *
     * @param scheduleId the schedule ID
     * @param triggerAt the trigger time
     * @return true if current broker owns a valid lease
     */
    public boolean verifyLease(String scheduleId, LocalDateTime triggerAt) {
        String brokerId = getCurrentBrokerId();
        if (brokerId == null) {
            return false;
        }

        // Use MySQL NOW(3) for consistent time - requires native query
        Long count = ((Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM schedule_delay " +
                "WHERE schedule_id = :scheduleId " +
                "AND trigger_at = :triggerAt " +
                "AND lease_owner = :brokerId " +
                "AND lease_until > NOW(3) " +
                "AND status = :status " +
                "AND deleted = false"
            )
            .setParameter("scheduleId", scheduleId)
            .setParameter("triggerAt", triggerAt)
            .setParameter("brokerId", brokerId)
            .setParameter("status", ScheduleDelay.Status.CLAIMED.value)
            .getSingleResult()).longValue();

        return count > 0;
    }

    /**
     * Transition a delay from CLAIMED to RUNNING status.
     * Only succeeds if the broker still owns the lease.
     *
     * @param scheduleId the schedule ID
     * @param triggerAt the trigger time
     * @return true if transition succeeded
     */
    public boolean transitionToRunning(String scheduleId, LocalDateTime triggerAt) {
        String brokerId = getCurrentBrokerId();
        if (brokerId == null) {
            return false;
        }

        return transactionService.transactional(() -> {
            int updated = entityManager.createNativeQuery(
                    "UPDATE schedule_delay " +
                    "SET status = :newStatus, updated_at = NOW(3) " +
                    "WHERE schedule_id = :scheduleId " +
                    "AND trigger_at = :triggerAt " +
                    "AND lease_owner = :brokerId " +
                    "AND lease_until > NOW(3) " +
                    "AND status = :currentStatus " +
                    "AND deleted = false"
                )
                .setParameter("newStatus", ScheduleDelay.Status.RUNNING.value)
                .setParameter("scheduleId", scheduleId)
                .setParameter("triggerAt", triggerAt)
                .setParameter("brokerId", brokerId)
                .setParameter("currentStatus", ScheduleDelay.Status.CLAIMED.value)
                .executeUpdate();

            return updated > 0;
        });
    }

    public int renewClaims(String brokerId, int durationSeconds) {
        return transactionService.transactional(() -> entityManager.createNativeQuery(
                "UPDATE schedule_delay " +
                "SET lease_until = DATE_ADD(NOW(3), INTERVAL :duration SECOND), updated_at = NOW(3) " +
                "WHERE lease_owner = :brokerId AND status = :status " +
                "AND lease_until > NOW(3) AND deleted = false"
            )
            .setParameter("duration", durationSeconds)
            .setParameter("brokerId", brokerId)
            .setParameter("status", ScheduleDelay.Status.CLAIMED.value)
            .executeUpdate());
    }

    public int reclaimExpiredClaims(java.util.List<Integer> buckets) {
        if (buckets.isEmpty()) {
            return 0;
        }
        return transactionService.transactional(() -> entityManager.createNativeQuery(
                "UPDATE schedule_delay " +
                "SET status = :initStatus, lease_owner = NULL, lease_until = NULL, updated_at = NOW(3) " +
                "WHERE bucket IN (:buckets) AND status = :claimedStatus " +
                "AND lease_until <= NOW(3) AND deleted = false"
            )
            .setParameter("initStatus", ScheduleDelay.Status.INIT.value)
            .setParameter("claimedStatus", ScheduleDelay.Status.CLAIMED.value)
            .setParameter("buckets", buckets)
            .executeUpdate());
    }

    private String getCurrentBrokerId() {
        if (BrokerContext.broker() == null) {
            return null;
        }
        return BrokerContext.broker().id();
    }
}
