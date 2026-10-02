/*
 * Copyright 2025-2030 Fluxion Team (https://github.com/Fluxion-io).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package io.fluxion.server.core.execution.service;

import io.fluxion.common.thread.CommonThreadPool;
import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.broker.query.BucketsByBrokerQuery;
import io.fluxion.server.core.execution.Executable;
import io.fluxion.server.core.execution.ExecutableType;
import io.fluxion.server.core.execution.Execution;
import io.fluxion.server.core.execution.ExecutionStatus;
import io.fluxion.server.core.execution.cmd.ExecutionsLoadCmd;
import io.fluxion.server.core.execution.query.ExecutableByIdQuery;
import io.fluxion.server.core.schedule.Schedule;
import io.fluxion.server.core.schedule.query.ScheduleByIdQuery;
import io.fluxion.server.core.execution.cmd.ExecutionScheduleFeedbackCmd;
import io.fluxion.server.core.trigger.Trigger;
import io.fluxion.server.core.trigger.query.TriggerByIdQuery;
import io.fluxion.server.infrastructure.dao.entity.ExecutionEntity;
import io.fluxion.server.infrastructure.dao.tx.TransactionService;
import io.fluxion.server.infrastructure.schedule.scheduler.DelayedTaskScheduler;
import io.fluxion.server.infrastructure.schedule.task.DelayedTaskFactory;
import io.fluxion.server.infrastructure.schedule.MisfirePolicy;
import io.limbo.cqrs.spring.annotation.CommandHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import io.limbo.cqrs.core.commandhandling.Cmd;
import io.limbo.cqrs.core.queryhandling.Query;

/**
 * Owns the scheduling-only portion of an Execution lifecycle.
 *
 * PENDING records are claimed with a short Broker lease.  The lease is only
 * used to protect Job creation; it is cleared in the same transaction that
 * creates Jobs and moves the Execution to RUNNING.
 */
@Slf4j
@Service
public class ExecutionScheduleCommandService {

    private static final int PRELOAD_SECONDS = 60;
    private static final int CLAIM_LEASE_SECONDS = 15;
    /** 错过触发判定阈值（秒），存量观测接口沿用同一事实源 */
    public static final int MISFIRE_THRESHOLD_SECONDS = 5;
    private static final int FIRE_RETRY_INTERVAL_SECONDS = 1;

    @Resource
    private EntityManager entityManager;

    @Resource
    private TransactionService transactionService;

    @CommandHandler
    public void handle(ExecutionsLoadCmd cmd) {
        if (BrokerContext.broker() == null) {
            return;
        }
        String brokerId = BrokerContext.broker().id();
        List<Integer> buckets = Query.query(new BucketsByBrokerQuery(brokerId)).getBuckets();
        if (CollectionUtils.isEmpty(buckets)) {
            return;
        }
        reclaimExpiredClaims(buckets);
        Instant endAt = Instant.now().plusSeconds(PRELOAD_SECONDS);
        List<ExecutionEntity> executions = entityManager.createQuery(
                "select e from ExecutionEntity e where e.deleted = false " +
                    "and e.status = :status and e.bucket in :buckets " +
                    "and e.triggerAt <= :endAt and (e.nextFireAt is null or e.nextFireAt <= :now) " +
                    "order by e.triggerAt asc", ExecutionEntity.class)
            .setParameter("status", ExecutionStatus.PENDING.value)
            .setParameter("buckets", buckets)
            .setParameter("endAt", endAt)
            .setParameter("now", Instant.now())
            .setMaxResults(100)
            .getResultList();
        for (ExecutionEntity execution : executions) {
            // 运行态均为 Instant 绝对时刻，Broker 预检查与 Worker 终检语义同一事实源（见 CONTEXT.md「统一时间模型」）
            if (isExpired(execution, Instant.now())) {
                finishPending(execution.getExecutionId(), ExecutionStatus.EXPIRED);
                continue;
            }
            boolean misfired = execution.getTriggerAt().isBefore(Instant.now().minusSeconds(MISFIRE_THRESHOLD_SECONDS));
            if (misfired) {
                Schedule schedule = Query.query(new ScheduleByIdQuery(execution.getTriggerId())).getSchedule();
                if (schedule == null || schedule.getOption().getMisfirePolicy() == MisfirePolicy.SKIP) {
                    finishPending(execution.getExecutionId(), ExecutionStatus.SKIPPED);
                    continue;
                }
                if (execution.getFireAttempt() >= schedule.getOption().getMaxFireAttempts()) {
                    finishPending(execution.getExecutionId(), ExecutionStatus.MISFIRED);
                    continue;
                }
            }
            String token = UUID.randomUUID().toString();
            if (!claim(execution.getExecutionId(), execution.getTriggerAt(), brokerId, token, misfired)) {
                continue;
            }
            DelayedTaskScheduler scheduler = BrokerContext.broker().delayedTaskScheduler();
            scheduler.schedule(DelayedTaskFactory.create(taskId(execution.getExecutionId()), execution.getTriggerAt(),
                // 时间轮线程只做移交：建 Job 事务交给有界 IO 池，慢 SQL/大量到点不再串行阻塞整个时间轮。
                // 顺序与重复由 claim 条件 UPDATE 仲裁；排队/被拒绝期间租约兜底（reclaim 后重领）。
                task -> CommonThreadPool.IO.execute(() -> runClaimedExecution(execution.getExecutionId(), brokerId, token))));
        }
    }

    protected boolean claim(String executionId, Instant triggerAt, String brokerId, String token, boolean misfired) {
        // 领取后到真正触发（建 Job）有一个预加载间隔，租约必须覆盖触发时刻，
        // 否则到点守卫会静默拒绝、只能依赖下轮 reclaim 重领（曾使约 75% 的预载执行首触发被拒）
        Instant leaseFrom = triggerAt != null && triggerAt.isAfter(Instant.now()) ? triggerAt : Instant.now();
        return transactionService.transactional(() -> entityManager.createQuery("update ExecutionEntity set status = :claimed, leaseOwner = :brokerId, " +
                "leaseUntil = :leaseUntil, executionToken = :token, fireAttempt = fireAttempt + :attemptIncrement " +
                "where executionId = :executionId and status = :pending and deleted = false")
            .setParameter("claimed", ExecutionStatus.CLAIMED.value)
            .setParameter("pending", ExecutionStatus.PENDING.value)
            .setParameter("brokerId", brokerId)
            .setParameter("leaseUntil", leaseFrom.plusSeconds(CLAIM_LEASE_SECONDS))
            .setParameter("token", token)
            .setParameter("attemptIncrement", misfired ? 1 : 0)
            .setParameter("executionId", executionId)
            .executeUpdate() == 1);
    }

    private void reclaimExpiredClaims(List<Integer> buckets) {
        transactionService.transactional(() -> entityManager.createQuery("update ExecutionEntity set status = :pending, " +
                "leaseOwner = null, leaseUntil = null, executionToken = null " +
                "where bucket in :buckets and status = :claimed and leaseUntil <= :now and deleted = false")
            .setParameter("pending", ExecutionStatus.PENDING.value)
            .setParameter("claimed", ExecutionStatus.CLAIMED.value)
            .setParameter("buckets", buckets)
            .setParameter("now", Instant.now())
            .executeUpdate());
    }

    protected void runClaimedExecution(String executionId, String brokerId, String token) {
        try {
            transactionService.transactional(() -> {
                runClaimedExecutionInTransaction(executionId, brokerId, token);
                return null;
            });
        } catch (RuntimeException e) {
            releaseForRetry(executionId, token);
            log.error("Execution {} failed before Job creation completed", executionId, e);
        }
    }

    private void runClaimedExecutionInTransaction(String executionId, String brokerId, String token) {
        ExecutionEntity entity = entityManager.find(ExecutionEntity.class, executionId);
        if (entity == null || !ExecutionStatus.CLAIMED.is(entity.getStatus())
                || !brokerId.equals(entity.getLeaseOwner()) || !token.equals(entity.getExecutionToken())
                || entity.getLeaseUntil() == null || !entity.getLeaseUntil().isAfter(Instant.now())) {
            return;
        }
        if (isExpired(entity, Instant.now())) {
            changeClaimedStatus(entity, ExecutionStatus.EXPIRED);
            return;
        }
        Trigger trigger = Query.query(new TriggerByIdQuery(entity.getTriggerId())).getTrigger();
        if (trigger == null || !trigger.isEnabled()) {
            changeClaimedStatus(entity, ExecutionStatus.INVALID);
            return;
        }
        Executable executable = Query.query(new ExecutableByIdQuery(
            entity.getExecutableId(), ExecutableType.parse(entity.getExecutableType()), entity.getExecutableVersion()
        )).getExecutable();
        if (executable == null) {
            changeClaimedStatus(entity, ExecutionStatus.MISFIRED);
            return;
        }

        // Job persistence and CLAIMED -> RUNNING are in this transaction.
        // 首批 Job 继承 Execution 的原始计划触发时刻（见 CONTEXT.md「Job 时间语义」）
        executable.execute(new Execution(entity.getExecutionId(), ExecutionStatus.CLAIMED,
            entity.getExecutableId(), entity.getExecutableVersion(), ExecutableType.parse(entity.getExecutableType()),
            entity.getDeadlineAt(), entity.getTriggerAt()));
        int updated = entityManager.createQuery("update ExecutionEntity set status = :running, startAt = :now, " +
                "leaseOwner = null, leaseUntil = null, executionToken = null " +
                "where executionId = :executionId and status = :claimed and leaseOwner = :brokerId " +
                "and executionToken = :token")
            .setParameter("running", ExecutionStatus.RUNNING.value)
            .setParameter("now", Instant.now())
            .setParameter("executionId", entity.getExecutionId())
            .setParameter("claimed", ExecutionStatus.CLAIMED.value)
            .setParameter("brokerId", brokerId)
            .setParameter("token", token)
            .executeUpdate();
        if (updated != 1) {
            throw new IllegalStateException("Execution fencing rejected: " + executionId);
        }
    }

    private void changeClaimedStatus(ExecutionEntity entity, ExecutionStatus status) {
        int updated = entityManager.createQuery("update ExecutionEntity set status = :status, endAt = :now, " +
                "leaseOwner = null, leaseUntil = null, executionToken = null " +
                "where executionId = :executionId and status = :claimed and executionToken = :token")
            .setParameter("status", status.value)
            .setParameter("now", Instant.now())
            .setParameter("executionId", entity.getExecutionId())
            .setParameter("claimed", ExecutionStatus.CLAIMED.value)
            .setParameter("token", entity.getExecutionToken())
            .executeUpdate();
        if (updated > 0) {
            // 与 finishPending 同理：领取后作废/补偿耗尽也是终态，必须反馈
            Cmd.send(new ExecutionScheduleFeedbackCmd(entity.getExecutionId()));
        }
    }

    private void finishPending(String executionId, ExecutionStatus status) {
        transactionService.transactional(() -> {
            int updated = entityManager.createQuery("update ExecutionEntity set status = :status, endAt = :now " +
                    "where executionId = :executionId and status = :pending and deleted = false")
                .setParameter("status", status.value)
                .setParameter("now", Instant.now())
                .setParameter("executionId", executionId)
                .setParameter("pending", ExecutionStatus.PENDING.value)
                .executeUpdate();
            if (updated > 0) {
                // 根据领域规则：未创建 Job 的终态同样必须反馈，否则 FIXED_DELAY 会永久停摆
                Cmd.send(new ExecutionScheduleFeedbackCmd(executionId));
            }
            return null;
        });
    }

    private void releaseForRetry(String executionId, String token) {
        transactionService.transactional(() -> entityManager.createQuery("update ExecutionEntity set status = :pending, " +
                "leaseOwner = null, leaseUntil = null, executionToken = null, nextFireAt = :nextFireAt " +
                "where executionId = :executionId and status = :claimed and executionToken = :token")
            .setParameter("pending", ExecutionStatus.PENDING.value)
            .setParameter("nextFireAt", Instant.now().plusSeconds(FIRE_RETRY_INTERVAL_SECONDS))
            .setParameter("executionId", executionId)
            .setParameter("claimed", ExecutionStatus.CLAIMED.value)
            .setParameter("token", token)
            .executeUpdate());
    }

    private String taskId(String executionId) {
        return "execution:" + executionId;
    }

    private boolean isExpired(ExecutionEntity execution, Instant now) {
        return execution.getDeadlineAt() != null && !execution.getDeadlineAt().isAfter(now);
    }
}
