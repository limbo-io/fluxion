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

package io.fluxion.server.core.schedule.service;

import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.broker.cmd.BucketAllotCmd;
import io.fluxion.server.core.broker.query.BucketsByBrokerQuery;
import io.fluxion.server.core.execution.Executable;
import io.fluxion.server.core.execution.Execution;
import io.fluxion.server.core.execution.cmd.ExecutionCreateCmd;
import io.fluxion.server.core.execution.query.ExecutableByIdQuery;
import io.fluxion.server.core.schedule.ScheduleDelay;
import io.fluxion.server.core.schedule.ScheduleLeaseMaintainer;
import io.fluxion.server.core.schedule.cmd.CancelTasksByBucketCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleDelayDeleteByIdsCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleDelayDeleteByScheduleCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleDelayReleaseClaimsCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleLeaseReclaimCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleLeaseRenewCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleDelaysCreateCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleDelaysLoadCmd;
import io.fluxion.server.core.schedule.converter.ScheduleDelayEntityConverter;
import io.fluxion.server.core.trigger.Trigger;
import io.fluxion.server.core.trigger.TriggerType;
import io.fluxion.server.core.trigger.query.TriggerByIdQuery;
import io.fluxion.server.infrastructure.dao.entity.ScheduleDelayEntity;
import io.fluxion.server.infrastructure.dao.repository.ScheduleDelayEntityRepo;
import io.fluxion.server.infrastructure.dao.tx.TransactionService;
import io.fluxion.server.infrastructure.schedule.scheduler.DelayedTaskScheduler;
import io.fluxion.server.infrastructure.schedule.task.DelayedTask;
import io.fluxion.server.infrastructure.schedule.task.DelayedTaskFactory;
import io.limbo.cqrs.spring.annotation.CommandHandler;
import io.limbo.cqrs.spring.command.Cmd;
import io.limbo.cqrs.spring.query.Query;
import io.limbo.utils.json.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * ScheduleDelayCommandService implements proper fencing for schedule state transitions.
 *
 * State machine with fencing:
 * - INIT → CLAIMED: claim with owner, 15s lease, attempt + 1
 * - CLAIMED → CLAIMED: renew lease (owner match + not expired)
 * - CLAIMED → RUNNING: begin execution (owner match + not expired), generate execution token
 * - RUNNING → SUCCEED/FAILED: finish execution (owner match + token match)
 * - CLAIMED → INIT: graceful release (owner match, clear owner/lease/token)
 * - RUNNING → reclaimable: lease expired, next broker can claim after expiration
 *
 * @author Devil
 */
@Slf4j
@Service
public class ScheduleDelayCommandService {

    @Resource
    private EntityManager entityManager;

    @Resource
    private ScheduleDelayEntityRepo scheduleDelayEntityRepo;

    @Resource
    private TransactionService transactionService;

    @Resource
    private ScheduleLeaseMaintainer leaseMaintainer;

    @CommandHandler
    public void handle(ScheduleDelaysCreateCmd cmd) {
        List<ScheduleDelay> delays = cmd.getDelays();
        List<ScheduleDelayEntity> entities = ScheduleDelayEntityConverter.convertToEntities(delays);
        if (CollectionUtils.isEmpty(entities)) {
            return;
        }
        for (ScheduleDelayEntity entity : entities) {
            int bucket = Cmd.send(new BucketAllotCmd(entity.getDelayId())).getBucket();
            entity.setBucket(bucket);
        }
        scheduleDelayEntityRepo.saveAllAndFlush(entities);
    }

    @CommandHandler
    public void handle(ScheduleLeaseRenewCmd cmd) {
        int renewed = leaseMaintainer.renewClaims(cmd.getBrokerId(), cmd.getLeaseDurationSeconds());
        log.debug("Renewed {} schedule leases for broker {}", renewed, cmd.getBrokerId());
    }

    @CommandHandler
    public void handle(ScheduleLeaseReclaimCmd cmd) {
        int reclaimed = leaseMaintainer.reclaimExpiredClaims(cmd.getBuckets());
        log.debug("Reclaimed {} expired schedule leases for buckets {}", reclaimed, cmd.getBuckets());
    }

    @Transactional
    @CommandHandler
    public void handle(ScheduleDelaysLoadCmd cmd) {
        List<ScheduleDelay> delays = cmd.getDelays();
        if (CollectionUtils.isEmpty(delays)) {
            return;
        }

        // Filter only INIT delays and attempt to claim them atomically
        for (ScheduleDelay delay : delays) {
            if (ScheduleDelay.Status.INIT != delay.getStatus()) {
                continue;
            }

            ScheduleDelay.ID delayId = delay.getId();

            // Only claim if trigger time is within the next minute
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.time.LocalDateTime oneMinuteLater = now.plusMinutes(1);

            if (delayId.getTriggerAt().isAfter(oneMinuteLater)) {
                continue;
            }

            boolean claimed = leaseMaintainer.tryClaim(delayId.getScheduleId(), delayId.getTriggerAt());
            if (!claimed) {
                log.debug("Failed to claim delay {}:{}, skipping", delayId.getScheduleId(), delayId.getTriggerAt());
                continue;
            }

            // Successfully claimed, now load into in-memory scheduler
            String scheduleId = delayId.getScheduleId();
            DelayedTaskScheduler delayedTaskScheduler = BrokerContext.broker().delayedTaskScheduler();
            delayedTaskScheduler.schedule(DelayedTaskFactory.create(
                delayId.toString(),
                delayId.getTriggerAt(),
                consumer(scheduleId, delayId)
            ));

            log.debug("Claimed and scheduled delay {}:{}", delayId.getScheduleId(), delayId.getTriggerAt());
        }
    }

    private Consumer<DelayedTask> consumer(String scheduleId, ScheduleDelay.ID delayId) {
        return task -> {
            String brokerId = getCurrentBrokerId();
            if (brokerId == null) {
                log.warn("No broker context for delay {}:{}, stopping task", scheduleId, delayId.getTriggerAt());
                task.stop();
                return;
            }

            // Fencing check: verify we still own the lease
            if (!leaseMaintainer.verifyLease(delayId.getScheduleId(), delayId.getTriggerAt())) {
                log.warn("Lease verification failed for delay {}:{}, fencing triggered", 
                    scheduleId, delayId.getTriggerAt());
                task.stop();
                return;
            }

            Trigger trigger = Query.query(new TriggerByIdQuery(scheduleId)).getTrigger();

            // Check if trigger is enabled
            if (!trigger.isEnabled()) {
                int affected = changeDelayStatus(delayId, ScheduleDelay.Status.CLAIMED, ScheduleDelay.Status.INVALID, brokerId, null);
                if (affected == 0) {
                    log.warn("Failed to transition delay {}:{} from CLAIMED to INVALID, possible fencing violation", 
                        scheduleId, delayId.getTriggerAt());
                } else {
                    log.info("Transitioned delay {}:{} to INVALID (trigger disabled), affected={}", 
                        scheduleId, delayId.getTriggerAt(), affected);
                }
                task.stop();
                return;
            }

            // Transition to RUNNING with execution token generation
            String executionToken = UUID.randomUUID().toString();
            int affected = changeDelayStatusToRunning(delayId, brokerId, executionToken);
            if (affected == 0) {
                log.warn("Failed to transition delay {}:{} to RUNNING, fencing rejected (rows=0)", scheduleId);
                task.stop();
                return;
            }

            log.info("Transitioned delay {}:{} to RUNNING, broker={}, token={}", 
                scheduleId, delayId.getTriggerAt(), brokerId, executionToken);

            try {
                Executable executable = Query.query(new ExecutableByIdQuery(
                    trigger.executableId(), trigger.getConfig().getExecuteConfig().type()
                )).getExecutable();
                transactionService.transactional(() -> {
                    Execution execution = Cmd.send(new ExecutionCreateCmd(
                        trigger.getId(),
                        TriggerType.SCHEDULE,
                        executable,
                        task.triggerAt()
                    )).getExecution();
                    executable.execute(execution);
                });
                
                // Success: RUNNING → SUCCEED with token validation
                int finishAffected = changeDelayStatus(delayId, ScheduleDelay.Status.RUNNING, 
                    ScheduleDelay.Status.SUCCEED, brokerId, executionToken);
                if (finishAffected == 0) {
                    log.error("Failed to transition delay {}:{} to SUCCEED, fencing rejected (rows=0). " +
                        "Possible concurrent execution or broker failover.", scheduleId, delayId.getTriggerAt());
                } else {
                    log.info("Transitioned delay {}:{} to SUCCEED, affected={}", 
                        scheduleId, delayId.getTriggerAt(), finishAffected);
                }
            } catch (Exception e) {
                log.error("ScheduleDelay run error id:{} broker={}", 
                    JacksonUtils.toJSONString(delayId), brokerId, e);
                
                // Failure: RUNNING → FAILED with token validation
                int finishAffected = changeDelayStatus(delayId, ScheduleDelay.Status.RUNNING, 
                    ScheduleDelay.Status.FAILED, brokerId, executionToken);
                if (finishAffected == 0) {
                    log.error("Failed to transition delay {}:{} to FAILED, fencing rejected (rows=0). " +
                        "Possible concurrent execution or broker failover.", scheduleId, delayId.getTriggerAt());
                } else {
                    log.info("Transitioned delay {}:{} to FAILED, affected={}", 
                        scheduleId, delayId.getTriggerAt(), finishAffected);
                }
            }
        };
    }

    /**
     * Change delay status with owner and execution token validation.
     * Uses native SQL with WHERE status = :old AND lease_owner = :owner 
     * AND (lease_until > NOW(3) OR :skipLeaseCheck = true) for fencing.
     *
     * @param delayId the delay ID
     * @param oldStatus expected current status
     * @param newStatus target status
     * @param owner the broker ID that must own the lease
     * @param executionToken the execution token (required for RUNNING terminal transitions)
     * @return number of affected rows (0 means fencing rejected)
     */
    private int changeDelayStatus(ScheduleDelay.ID delayId, ScheduleDelay.Status oldStatus, 
                                   ScheduleDelay.Status newStatus, String owner, String executionToken) {
        return transactionService.transactional(() -> {
            String scheduleId = delayId.getScheduleId();
            LocalDateTime triggerAt = delayId.getTriggerAt();
            
            // Build native SQL with conditional fencing
            StringBuilder sql = new StringBuilder(
                "UPDATE schedule_delay " +
                "SET status = :newStatus, updated_at = CURRENT_TIMESTAMP(3)"
            );
            
            // Clear execution token when leaving RUNNING
            if (oldStatus == ScheduleDelay.Status.RUNNING) {
                sql.append(", execution_token = NULL");
            }
            
            sql.append(" WHERE schedule_id = :scheduleId " +
                "AND trigger_at = :triggerAt " +
                "AND status = :oldStatus " +
                "AND lease_owner = :owner " +
                "AND deleted = false");
            
            // Add execution token check for RUNNING → terminal transitions
            if (oldStatus == ScheduleDelay.Status.RUNNING && executionToken != null) {
                sql.append(" AND execution_token = :executionToken");
            }
            
            sql.append(" AND lease_until > NOW(3)");
            
            var query = entityManager.createNativeQuery(sql.toString())
                .setParameter("newStatus", newStatus.value)
                .setParameter("scheduleId", scheduleId)
                .setParameter("triggerAt", triggerAt)
                .setParameter("oldStatus", oldStatus.value)
                .setParameter("owner", owner);
            
            if (oldStatus == ScheduleDelay.Status.RUNNING && executionToken != null) {
                query.setParameter("executionToken", executionToken);
            }
            
            int affected = query.executeUpdate();
            
            log.debug("changeDelayStatus: {}:{} {}→{} by owner={}, affected={}, token={}", 
                scheduleId, triggerAt, oldStatus, newStatus, owner, affected, 
                executionToken != null ? "present" : "null");
            
            return affected;
        });
    }

    /**
     * Transition from CLAIMED to RUNNING with execution token generation.
     * This is a special case because we need to SET the execution token.
     */
    private int changeDelayStatusToRunning(ScheduleDelay.ID delayId, String owner, String executionToken) {
        return transactionService.transactional(() -> {
            String scheduleId = delayId.getScheduleId();
            LocalDateTime triggerAt = delayId.getTriggerAt();
            
            String sql = "UPDATE schedule_delay " +
                "SET status = :newStatus, execution_token = :executionToken, updated_at = CURRENT_TIMESTAMP(3) " +
                "WHERE schedule_id = :scheduleId " +
                "AND trigger_at = :triggerAt " +
                "AND status = :oldStatus " +
                "AND lease_owner = :owner " +
                "AND lease_until > NOW(3) " +
                "AND deleted = false";
            
            int affected = entityManager.createNativeQuery(sql)
                .setParameter("newStatus", ScheduleDelay.Status.RUNNING.value)
                .setParameter("executionToken", executionToken)
                .setParameter("scheduleId", scheduleId)
                .setParameter("triggerAt", triggerAt)
                .setParameter("oldStatus", ScheduleDelay.Status.CLAIMED.value)
                .setParameter("owner", owner)
                .executeUpdate();
            
            log.debug("changeDelayStatusToRunning: {}:{} CLAIMED→RUNNING by owner={}, affected={}, token={}", 
                scheduleId, triggerAt, owner, affected, executionToken);
            
            return affected;
        });
    }

    /**
     * Gracefully release CLAIMED delays for a broker during shutdown.
     * Only releases CLAIMED records with owner match (not RUNNING).
     * Atomic update: CLAIMED → INIT with owner check, clears owner/lease/token.
     *
     * @param brokerId the broker ID whose claims should be released
     * @return number of claims released
     */
    @Transactional
    public int releaseClaimsForBroker(String brokerId) {
        // Only reset CLAIMED → INIT, not RUNNING
        // RUNNING records are handled by fault-tolerance (execution already created)
        String sql = "UPDATE schedule_delay " +
            "SET status = :initStatus, lease_owner = NULL, lease_until = NULL, " +
            "    execution_token = NULL, updated_at = CURRENT_TIMESTAMP(3) " +
            "WHERE lease_owner = :brokerId " +
            "AND status = :claimedStatus " +
            "AND deleted = false";
        
        int affected = entityManager.createNativeQuery(sql)
            .setParameter("initStatus", ScheduleDelay.Status.INIT.value)
            .setParameter("brokerId", brokerId)
            .setParameter("claimedStatus", ScheduleDelay.Status.CLAIMED.value)
            .executeUpdate();
        
        if (affected > 0) {
            log.info("Released {} CLAIMED schedule delays for broker {} (CLAIMED→INIT)", affected, brokerId);
        }
        
        return affected;
    }

    /**
     * Recover RUNNING delays that have expired leases.
     * This handles the case where a broker crashed while executing.
     *
     * Recovery rules:
     * - If execution not yet created: lease expired → reclaim → INIT
     * - If execution created: fault-tolerance handles, don't duplicate
     *
     * @return number of delays recovered
     */
    @Transactional
    public int recoverExpiredRunningDelays() {
        // Find RUNNING delays with expired leases
        // These can be safely reset to INIT if no execution exists
        // The execution service handles duplicate prevention at execution creation time
        
        String findSql = "SELECT schedule_id, trigger_at, lease_owner, execution_token " +
            "FROM schedule_delay " +
            "WHERE status = :runningStatus " +
            "AND lease_until < DATE_SUB(NOW(3), INTERVAL 30 SECOND) " +
            "AND deleted = false";
        
        @SuppressWarnings("unchecked")
        List<Object[]> expiredDelays = entityManager.createNativeQuery(findSql)
            .setParameter("runningStatus", ScheduleDelay.Status.RUNNING.value)
            .getResultList();
        
        if (CollectionUtils.isEmpty(expiredDelays)) {
            return 0;
        }
        
        int recovered = 0;
        for (Object[] row : expiredDelays) {
            String scheduleId = (String) row[0];
            LocalDateTime triggerAt = ((java.sql.Timestamp) row[1]).toLocalDateTime();
            String oldOwner = (String) row[2];
            String oldToken = (String) row[3];
            
            // Reset to INIT - execution service will handle duplicates at creation time
            String updateSql = "UPDATE schedule_delay " +
                "SET status = :initStatus, lease_owner = NULL, lease_until = NULL, " +
                "    execution_token = NULL, updated_at = CURRENT_TIMESTAMP(3) " +
                "WHERE schedule_id = :scheduleId " +
                "AND trigger_at = :triggerAt " +
                "AND status = :runningStatus " +
                "AND lease_owner = :oldOwner " +
                "AND deleted = false";
            
            int affected = entityManager.createNativeQuery(updateSql)
                .setParameter("initStatus", ScheduleDelay.Status.INIT.value)
                .setParameter("scheduleId", scheduleId)
                .setParameter("triggerAt", triggerAt)
                .setParameter("runningStatus", ScheduleDelay.Status.RUNNING.value)
                .setParameter("oldOwner", oldOwner)
                .executeUpdate();
            
            if (affected > 0) {
                recovered++;
                log.warn("Recovered expired RUNNING delay {}:{} (oldOwner={}, oldToken={}) → INIT", 
                    scheduleId, triggerAt, oldOwner, 
                    oldToken != null ? oldToken.substring(0, 8) + "..." : "null");
            }
        }
        
        if (recovered > 0) {
            log.info("Recovered {} expired RUNNING delays to INIT", recovered);
        }
        
        return recovered;
    }

    @Transactional
    @CommandHandler
    public void handle(ScheduleDelayReleaseClaimsCmd cmd) {
        int released = releaseClaimsForBroker(cmd.getBrokerId());
        log.info("Released {} claimed schedule delays for broker {} during graceful shutdown", 
            released, cmd.getBrokerId());
    }

    @Transactional
    @CommandHandler
    public void handle(ScheduleDelayDeleteByScheduleCmd cmd) {
        entityManager.createQuery("update ScheduleDelayEntity " +
                "set deleted = :deleted " +
                "where id.scheduleId = :scheduleId and status in :statuses"
            )
            .setParameter("statuses", cmd.getStatuses().stream().map(s -> s.value).collect(Collectors.toList()))
            .setParameter("deleted", true)
            .setParameter("scheduleId", cmd.getScheduleId())
            .executeUpdate();
    }

    @Transactional
    @CommandHandler
    public void handle(ScheduleDelayDeleteByIdsCmd cmd) {
        if (CollectionUtils.isEmpty(cmd.getIds())) {
            return;
        }
        List<ScheduleDelayEntity.ID> ids = cmd.getIds().stream().map(ScheduleDelayEntityConverter::convert).collect(Collectors.toList());
        scheduleDelayEntityRepo.deleteAllById(ids);
    }

    @CommandHandler
    public void handle(CancelTasksByBucketCmd cmd) {
        cancelTasksForBuckets(cmd.getBuckets());
    }

    /**
     * Cancel in-memory scheduled tasks for specific buckets.
     * This stops the tasks in the delayedTaskScheduler but does not modify database state.
     * The database leases will expire naturally and be reclaimed by the new bucket owner.
     *
     * @param buckets list of bucket numbers whose tasks should be cancelled
     */
    public void cancelTasksForBuckets(List<Integer> buckets) {
        if (CollectionUtils.isEmpty(buckets)) {
            return;
        }
        
        DelayedTaskScheduler scheduler = BrokerContext.broker().delayedTaskScheduler();
        
        LocalDateTime now = LocalDateTime.now();
        List<ScheduleDelayEntity> delays = entityManager.createQuery(
                "SELECT e FROM ScheduleDelayEntity e " +
                "WHERE e.bucket IN :buckets " +
                "AND e.status = :status " +
                "AND e.deleted = false " +
                "AND e.id.triggerAt > :now",
                ScheduleDelayEntity.class
            )
            .setParameter("buckets", buckets)
            .setParameter("status", ScheduleDelay.Status.CLAIMED.value)
            .setParameter("now", now)
            .getResultList();
        
        int cancelled = 0;
        for (ScheduleDelayEntity delay : delays) {
            String taskId = delay.getId().getScheduleId() + ":" + delay.getId().getTriggerAt();
            scheduler.stop(taskId);
            cancelled++;
        }
        
        if (cancelled > 0) {
            log.info("Cancelled {} in-memory tasks for buckets {}", cancelled, buckets);
        }
    }

    private String getCurrentBrokerId() {
        if (BrokerContext.broker() == null) {
            return null;
        }
        return BrokerContext.broker().id();
    }
}
