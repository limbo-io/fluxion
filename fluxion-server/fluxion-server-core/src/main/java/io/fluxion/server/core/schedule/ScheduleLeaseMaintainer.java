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
import io.fluxion.server.core.broker.query.BucketsByBrokerQuery;
import io.fluxion.server.infrastructure.dao.entity.ScheduleDelayEntity;
import io.fluxion.server.infrastructure.dao.tx.TransactionService;
import io.limbo.cqrs.spring.query.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

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

    /**
     * Initial lease duration: 15 seconds
     */
    private static final int LEASE_SECONDS = 15;

    /**
     * Renew owned leases every 10 seconds to prevent expiration
     */
    private static final long RENEW_INTERVAL_MS = 10000;

    /**
     * Scan for reclaimable leases every 5 seconds
     */
    private static final long RECLAIM_INTERVAL_MS = 5000;

    @Resource
    private EntityManager entityManager;

    @Resource
    private TransactionService transactionService;

    /**
     * Renew leases for delays owned by current broker.
     * Scheduled every 10 seconds to ensure leases don't expire before next renewal.
     */
    @Scheduled(fixedRate = RENEW_INTERVAL_MS)
    public void renewLeases() {
        String brokerId = getCurrentBrokerId();
        if (brokerId == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime newLeaseUntil = now.plusSeconds(LEASE_SECONDS);

        try {
            int renewed = transactionService.transactional(() ->
                entityManager.createQuery(
                        "UPDATE ScheduleDelayEntity e " +
                        "SET e.leaseUntil = :newLeaseUntil, e.updatedAt = :now " +
                        "WHERE e.leaseOwner = :brokerId " +
                        "AND e.status = :status " +
                        "AND e.deleted = false"
                    )
                    .setParameter("newLeaseUntil", newLeaseUntil)
                    .setParameter("now", now)
                    .setParameter("brokerId", brokerId)
                    .setParameter("status", ScheduleDelay.Status.CLAIMED.value)
                    .executeUpdate()
            );

            if (renewed > 0 && log.isDebugEnabled()) {
                log.debug("Renewed {} lease(s) for broker {}", renewed, brokerId);
            }
        } catch (Exception e) {
            log.error("Failed to renew leases for broker {}", brokerId, e);
        }
    }

    /**
     * Scan for expired leases that can be reclaimed.
     * Scheduled every 5 seconds to allow failover within ~20 seconds.
     * <p>
     * Marks expired CLAIMED delays as INIT so they can be re-claimed.
     */
    @Scheduled(fixedRate = RECLAIM_INTERVAL_MS)
    public void reclaimExpiredLeases() {
        String brokerId = getCurrentBrokerId();
        if (brokerId == null) {
            return;
        }

        List<Integer> buckets = getCurrentBrokerBuckets();
        if (buckets.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        try {
            // Find and reset delays with expired leases back to INIT status
            // This allows them to be re-claimed by any broker (including current if applicable)
            int reclaimed = transactionService.transactional(() ->
                entityManager.createQuery(
                        "UPDATE ScheduleDelayEntity e " +
                        "SET e.status = :newStatus, e.leaseOwner = NULL, " +
                        "    e.leaseUntil = NULL, e.updatedAt = :now " +
                        "WHERE e.bucket IN :buckets " +
                        "AND e.status = :currentStatus " +
                        "AND e.leaseUntil < :now " +
                        "AND e.deleted = false"
                    )
                    .setParameter("newStatus", ScheduleDelay.Status.INIT.value)
                    .setParameter("buckets", buckets)
                    .setParameter("currentStatus", ScheduleDelay.Status.CLAIMED.value)
                    .setParameter("now", now)
                    .executeUpdate()
            );

            if (reclaimed > 0) {
                log.info("Reclaimed {} expired lease(s) for buckets {}, broker {}",
                    reclaimed, buckets, brokerId);
            }
        } catch (Exception e) {
            log.error("Failed to reclaim expired leases for broker {}", brokerId, e);
        }
    }

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

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime leaseUntil = now.plusSeconds(LEASE_SECONDS);

        return transactionService.transactional(() -> {
            int updated = entityManager.createQuery(
                    "UPDATE ScheduleDelayEntity e " +
                    "SET e.leaseOwner = :brokerId, e.leaseUntil = :leaseUntil, " +
                    "    e.status = :newStatus, e.attempt = e.attempt + 1, e.updatedAt = :now " +
                    "WHERE e.id.scheduleId = :scheduleId " +
                    "AND e.id.triggerAt = :triggerAt " +
                    "AND e.status = :initStatus " +
                    "AND e.deleted = false"
                )
                .setParameter("brokerId", brokerId)
                .setParameter("leaseUntil", leaseUntil)
                .setParameter("newStatus", ScheduleDelay.Status.CLAIMED.value)
                .setParameter("now", now)
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

        LocalDateTime now = LocalDateTime.now();

        Long count = entityManager.createQuery(
                "SELECT COUNT(e) FROM ScheduleDelayEntity e " +
                "WHERE e.id.scheduleId = :scheduleId " +
                "AND e.id.triggerAt = :triggerAt " +
                "AND e.leaseOwner = :brokerId " +
                "AND e.leaseUntil > :now " +
                "AND e.status = :status " +
                "AND e.deleted = false",
                Long.class
            )
            .setParameter("scheduleId", scheduleId)
            .setParameter("triggerAt", triggerAt)
            .setParameter("brokerId", brokerId)
            .setParameter("now", now)
            .setParameter("status", ScheduleDelay.Status.CLAIMED.value)
            .getSingleResult();

        return count != null && count > 0;
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

        LocalDateTime now = LocalDateTime.now();

        return transactionService.transactional(() -> {
            int updated = entityManager.createQuery(
                    "UPDATE ScheduleDelayEntity e " +
                    "SET e.status = :newStatus, e.updatedAt = :now " +
                    "WHERE e.id.scheduleId = :scheduleId " +
                    "AND e.id.triggerAt = :triggerAt " +
                    "AND e.leaseOwner = :brokerId " +
                    "AND e.leaseUntil > :now " +
                    "AND e.status = :currentStatus " +
                    "AND e.deleted = false"
                )
                .setParameter("newStatus", ScheduleDelay.Status.RUNNING.value)
                .setParameter("now", now)
                .setParameter("scheduleId", scheduleId)
                .setParameter("triggerAt", triggerAt)
                .setParameter("brokerId", brokerId)
                .setParameter("currentStatus", ScheduleDelay.Status.CLAIMED.value)
                .executeUpdate();

            return updated > 0;
        });
    }

    private String getCurrentBrokerId() {
        if (BrokerContext.broker() == null) {
            return null;
        }
        return BrokerContext.broker().id();
    }

    private List<Integer> getCurrentBrokerBuckets() {
        String brokerId = getCurrentBrokerId();
        if (brokerId == null) {
            return java.util.Collections.emptyList();
        }
        return Query.query(new BucketsByBrokerQuery(brokerId)).getBuckets();
    }
}
