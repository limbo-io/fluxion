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
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
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

            // Attempt to claim the delay with lease mechanism
            // Only claim if trigger time is within the next minute (to avoid claiming too far in advance)
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.time.LocalDateTime oneMinuteLater = now.plusMinutes(1);

            if (delayId.getTriggerAt().isAfter(oneMinuteLater)) {
                // Delay is too far in the future, skip for now
                // It will be loaded in a future iteration when closer to trigger time
                continue;
            }

            boolean claimed = leaseMaintainer.tryClaim(delayId.getScheduleId(), delayId.getTriggerAt());
            if (!claimed) {
                // Another broker claimed it or it's no longer available
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
            // First, verify we still own the lease (fencing check)
            // This prevents split-brain scenarios where broker lost lease but task still fired
            if (!leaseMaintainer.verifyLease(delayId.getScheduleId(), delayId.getTriggerAt())) {
                log.warn("Lease verification failed for delay {}:{}, stopping task", scheduleId, delayId.getTriggerAt());
                task.stop();
                return;
            }

            Trigger trigger = Query.query(new TriggerByIdQuery(scheduleId)).getTrigger();

            // Check if trigger is enabled
            if (!trigger.isEnabled()) {
                // Transition from CLAIMED to INVALID
                changeDelayStatus(delayId, ScheduleDelay.Status.CLAIMED, ScheduleDelay.Status.INVALID);
                task.stop();
                log.info("Trigger is not enabled id:{}", scheduleId);
                return;
            }

            // Verify we can transition from CLAIMED to RUNNING atomically with lease check
            // This guards against concurrent execution attempts
            if (!leaseMaintainer.transitionToRunning(delayId.getScheduleId(), delayId.getTriggerAt())) {
                log.info("Failed to transition delay {} to RUNNING, another broker may be processing",
                    scheduleId);
                task.stop();
                return;
            }

            try {
                Executable executable = Query.query(new ExecutableByIdQuery(
                    trigger.executableId(), trigger.getConfig().getExecuteConfig().type()
                )).getExecutable();
                transactionService.transactional(() -> {
                    // 创建执行记录
                    Execution execution = Cmd.send(new ExecutionCreateCmd(
                        trigger.getId(),
                        TriggerType.SCHEDULE,
                        executable,
                        task.triggerAt()
                    )).getExecution();
                    // 执行
                    executable.execute(execution);
                });
                // 成功状态: RUNNING -> SUCCEED
                changeDelayStatus(delayId, ScheduleDelay.Status.RUNNING, ScheduleDelay.Status.SUCCEED);
            } catch (Exception e) {
                log.error("ScheduleDelay run error id:{}", JacksonUtils.toJSONString(delayId), e);
                changeDelayStatus(delayId, ScheduleDelay.Status.RUNNING, ScheduleDelay.Status.FAILED);
            }
        };
    }

    private int changeDelayStatus(List<ScheduleDelay.ID> delayIds, ScheduleDelay.Status oldStatus, ScheduleDelay.Status newStatus) {
        return transactionService.transactional(() -> entityManager.createQuery(
                "update ScheduleDelayEntity " +
                    "set status = :newStatus " +
                    "where id in :ids and status = :oldStatus"
            )
            .setParameter("newStatus", newStatus.value)
            .setParameter("oldStatus", oldStatus.value)
            .setParameter("ids", ScheduleDelayEntityConverter.convertToEntityIds(delayIds))
            .executeUpdate());
    }

    private boolean changeDelayStatus(ScheduleDelay.ID delayId, ScheduleDelay.Status oldStatus, ScheduleDelay.Status newStatus) {
        return transactionService.transactional(() ->
            changeDelayStatus(Collections.singletonList(delayId), oldStatus, newStatus) > 0
        );
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

    /**
     * Release all claimed schedule delays for a specific broker.
     * This sets the lease_owner to NULL, allowing other brokers to reclaim
     * these delays after the lease expires.
     *
     * @param brokerId the broker ID whose claims should be released
     * @return number of claims released
     */
    @Transactional
    public int releaseClaimsForBroker(String brokerId) {
        LocalDateTime now = LocalDateTime.now();
        
        return entityManager.createQuery(
                "UPDATE ScheduleDelayEntity e " +
                "SET e.leaseOwner = NULL, e.leaseUntil = NULL, e.updatedAt = :now " +
                "WHERE e.leaseOwner = :brokerId " +
                "AND e.status = :status " +
                "AND e.deleted = false"
            )
            .setParameter("brokerId", brokerId)
            .setParameter("status", ScheduleDelay.Status.CLAIMED.value)
            .setParameter("now", now)
            .executeUpdate();
    }

    @Transactional
    @CommandHandler
    public void handle(ScheduleDelayReleaseClaimsCmd cmd) {
        int released = releaseClaimsForBroker(cmd.getBrokerId());
        log.info("Released {} claimed schedule delays for broker {}", released, cmd.getBrokerId());
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
        
        // Get the delayed task scheduler from broker context
        DelayedTaskScheduler scheduler = BrokerContext.broker().delayedTaskScheduler();
        
        // Query all active delays in these buckets to get their task IDs
        // The task ID format is: scheduleId + ":" + triggerAt (see ScheduleDelay.ID.toString())
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
            // The task ID is the same as ScheduleDelay.ID.toString()
            String taskId = delay.getId().getScheduleId() + ":" + delay.getId().getTriggerAt();
            scheduler.stop(taskId);
            cancelled++;
        }
        
        if (cancelled > 0) {
            log.info("Cancelled {} in-memory tasks for buckets {}", cancelled, buckets);
        }
    }
}
