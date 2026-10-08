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

package io.fluxion.server.core.execution.service;

import io.fluxion.server.core.execution.Executable;
import io.fluxion.server.core.execution.Execution;
import io.fluxion.server.core.execution.ExecutionStatus;
import io.fluxion.server.core.execution.cmd.ExecutionCreateCmd;
import io.fluxion.server.core.execution.cmd.ExecutionExpiredCmd;
import io.fluxion.server.core.execution.cmd.ExecutionCleanCmd;
import io.fluxion.server.core.execution.cmd.ExecutionFailCmd;
import io.fluxion.server.core.execution.cmd.ExecutionRunningCmd;
import io.fluxion.server.core.execution.cmd.ExecutionScheduleFeedbackCmd;
import io.fluxion.server.core.execution.cmd.ExecutionSuccessCmd;
import io.fluxion.server.core.schedule.Schedule;
import io.fluxion.server.core.schedule.cmd.ScheduleFeedbackCmd;
import io.fluxion.server.core.schedule.query.ScheduleByIdQuery;
import io.fluxion.server.core.trigger.TriggerType;
import io.fluxion.server.infrastructure.dao.entity.ExecutionEntity;
import io.fluxion.server.infrastructure.dao.repository.ExecutionEntityRepo;
import io.fluxion.server.infrastructure.id.cmd.IDGenerateCmd;
import io.fluxion.server.infrastructure.id.data.IDType;
import lombok.extern.slf4j.Slf4j;
import io.limbo.cqrs.spring.annotation.CommandHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.Instant;
import io.limbo.cqrs.core.commandhandling.Cmd;
import io.limbo.cqrs.core.queryhandling.Query;

/**
 * @author Devil
 */
@Slf4j
@Service
public class ExecutionCommandService {

    @Resource
    private ExecutionEntityRepo executionEntityRepo;

    @Resource
    private EntityManager entityManager;

    /**
     * 以 (triggerId, triggerAt) 身份唯一键为目标的幂等创建：已存在（或并发竞争）时返回
     * 既有实例（created=false），不抛唯一键异常；本次真正落库时 created=true。
     * 常见路径先查后写；并发盲区由 {@link ExecutionEntityRepo#insertExecutionIdentity}
     * 的数据库幂等写入兼底——回查行的 executionId 判定归属，不依赖驱动返回行数。
     */
    @Transactional
    @CommandHandler
    public ExecutionCreateCmd.Response handle(ExecutionCreateCmd cmd) {
        Executable executable = cmd.getExecutable();
        // 快速路径：先查能查见的已存在实例，避免无谓的身份证号写入
        ExecutionEntity entity = executionEntityRepo.findByTriggerIdAndTriggerAt(cmd.getTriggerId(), cmd.getTriggerAt());
        boolean created = entity == null;
        if (created) {
            String executionId = Cmd.send(new IDGenerateCmd(IDType.EXECUTION)).getId();
            Instant deadlineAt = null;
            if (cmd.getTriggerType() == TriggerType.SCHEDULE) {
                Schedule schedule = Query.query(new ScheduleByIdQuery(cmd.getTriggerId())).getSchedule();
                Long maxStartDelayMillis = schedule == null ? null : schedule.getOption().getMaxStartDelayMillis();
                if (maxStartDelayMillis != null) {
                    deadlineAt = cmd.getTriggerAt().plusNanos(maxStartDelayMillis * 1_000_000);
                }
            }
            String status = cmd.getTriggerType() == TriggerType.SCHEDULE
                ? ExecutionStatus.PENDING.value : ExecutionStatus.INITED.value;
            executionEntityRepo.insertExecutionIdentity(executionId,
                cmd.getTriggerId(), cmd.getTriggerType().value,
                executable.id(), executable.type().value, executable.version(),
                status, cmd.getTriggerAt(), deadlineAt,
                Math.floorMod(executionId.hashCode(), 64) + 1);
            // 回查必须走加锁当前读：REPEATABLE READ 事务的普通快照读若在先行者提交前已
            // 建立，将永远看不到对方行（快照出生早于提交）；悲观锁读取最新已提交行。
            entity = executionEntityRepo.findWithLockingByTriggerIdAndTriggerAt(
                cmd.getTriggerId(), cmd.getTriggerAt());
            // 回查结果必然非空：本事务已写入或并发方已提交；并将身份归属判定为 CREATED/EXISTING
            if (entity == null) {
                throw new IllegalStateException("execution identity not found after upsert, triggerId:"
                    + cmd.getTriggerId() + " triggerAt:" + cmd.getTriggerAt());
            }
            created = executionId.equals(entity.getExecutionId());
        }
        Execution execution = new Execution(
            entity.getExecutionId(), ExecutionStatus.parse(entity.getStatus()),
            entity.getExecutableId(), entity.getExecutableVersion(), executable.type(), entity.getDeadlineAt(),
            entity.getTriggerAt()
        );
        return new ExecutionCreateCmd.Response(execution, created);
    }

    @Transactional
    @CommandHandler
    public void handle(ExecutionRunningCmd cmd) {
        entityManager.createQuery("update ExecutionEntity " +
                "set status = :newStatus, startAt = :startAt " +
                "where executionId = :executionId and status = :oldStatus"
            )
            .setParameter("newStatus", ExecutionStatus.RUNNING.value)
            .setParameter("executionId", cmd.getExecutionId())
            .setParameter("oldStatus", ExecutionStatus.INITED.value)
            .setParameter("startAt", Instant.now())
            .executeUpdate();
    }

    @Transactional
    @CommandHandler
    public boolean handle(ExecutionSuccessCmd cmd) {
        boolean finished = updateToFinish(cmd.getExecutionId(), ExecutionStatus.SUCCEED, cmd.getEndAt());
        if (!finished) {
            log.warn("ExecutionSuccessCmd update fail executionId:{}", cmd.getExecutionId());
            return false;
        }
        afterFinsh(cmd.getExecutionId());
        return true;
    }

    @Transactional
    @CommandHandler
    public boolean handle(ExecutionFailCmd cmd) {
        boolean finished = updateToFinish(cmd.getExecutionId(), ExecutionStatus.FAILED, cmd.getEndAt());
        if (!finished) {
            log.warn("ExecutionFailCmd update fail executionId:{}", cmd.getExecutionId());
            return false;
        }
        afterFinsh(cmd.getExecutionId());
        return true;
    }

    @Transactional
    @CommandHandler
    public void handle(ExecutionCleanCmd cmd) {
        entityManager.createQuery("update ExecutionEntity set deleted = true " +
                "where endAt <= :endAt and status in :statuses and deleted = false")
            .setParameter("endAt", cmd.getEndAt())
            .setParameter("statuses", java.util.Arrays.asList(
                ExecutionStatus.SUCCEED.value, ExecutionStatus.FAILED.value,
                ExecutionStatus.SKIPPED.value, ExecutionStatus.MISFIRED.value,
                ExecutionStatus.EXPIRED.value,
                ExecutionStatus.INVALID.value
            ))
            .executeUpdate();
    }

    private boolean updateToFinish(String executionId, ExecutionStatus status, Instant endTime) {
        return entityManager.createQuery("update ExecutionEntity " +
                "set status = :newStatus, endAt = :endAt " +
                "where executionId = :executionId and status = :oldStatus "
            )
            .setParameter("endAt", endTime)
            .setParameter("executionId", executionId)
            .setParameter("oldStatus", ExecutionStatus.RUNNING.value)
            .setParameter("newStatus", status.value)
            .executeUpdate() > 0;
    }

    /**
     * 首批 Job 因激活截止时间被拒：Execution 由 RUNNING 收敛为 EXPIRED，并发反馈。
     * 不影响同 Execution 的其它 Job（下游节点无截止时间，不会走此路）。
     */
    @Transactional
    @CommandHandler
    public boolean handle(ExecutionExpiredCmd cmd) {
        int updated = entityManager.createQuery("update ExecutionEntity " +
                "set status = :newStatus, endAt = :endAt " +
                "where executionId = :executionId and status = :oldStatus"
            )
            .setParameter("newStatus", ExecutionStatus.EXPIRED.value)
            .setParameter("endAt", cmd.getEndAt())
            .setParameter("executionId", cmd.getExecutionId())
            .setParameter("oldStatus", ExecutionStatus.RUNNING.value)
            .executeUpdate();
        if (updated <= 0) {
            // Execution 已进入其它终态，旧拒绝结果不生效
            log.warn("ExecutionExpiredCmd update fail executionId:{}", cmd.getExecutionId());
            return false;
        }
        // EXPIRED 也是终态，必须反馈，否则 FIXED_DELAY 会永久停摆（CONTEXT.md「调度反馈」）
        afterFinsh(cmd.getExecutionId());
        return true;
    }

    /** 承接 Execution 侧发出的调度反馈触发：同一事务内解析 Schedule 并转发 {@code ScheduleFeedbackCmd} */
    @Transactional
    @CommandHandler
    public void handle(ExecutionScheduleFeedbackCmd cmd) {
        afterFinsh(cmd.getExecutionId());
    }

    private void afterFinsh(String executionId) {
        ExecutionEntity entity = executionEntityRepo.findById(executionId).get();
        if (TriggerType.SCHEDULE != TriggerType.parse(entity.getTriggerType())) {
            return;
        }
        Schedule schedule = Query.query(new ScheduleByIdQuery(entity.getTriggerId())).getSchedule();
        // 调度已删除：无需反馈，避免反馈链路图空指针回滚终态迁移
        if (schedule == null) {
            return;
        }
        Cmd.send(new ScheduleFeedbackCmd(schedule));
    }

}
