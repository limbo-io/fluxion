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

import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.execution.Executable;
import io.fluxion.server.core.execution.Execution;
import io.fluxion.server.core.execution.ExecutionStatus;
import io.fluxion.server.core.execution.cmd.ExecutionCreateCmd;
import io.fluxion.server.core.execution.cmd.ExecutionFailCmd;
import io.fluxion.server.core.execution.cmd.ExecutionSuccessCmd;
import io.fluxion.server.core.execution.query.ExecutableByIdQuery;
import io.fluxion.server.core.trigger.TriggerHelper;
import io.fluxion.server.core.trigger.cmd.ScheduleRefreshLastFeedbackCmd;
import io.fluxion.server.core.trigger.query.ScheduleByIdQuery;
import io.fluxion.server.core.trigger.run.Schedule;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.dao.entity.ExecutionEntity;
import io.fluxion.server.infrastructure.dao.repository.ExecutionEntityRepo;
import io.fluxion.server.infrastructure.exception.ErrorCode;
import io.fluxion.server.infrastructure.exception.PlatformException;
import io.fluxion.server.infrastructure.id.cmd.IDGenerateCmd;
import io.fluxion.server.infrastructure.id.data.IDType;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import io.fluxion.server.infrastructure.schedule.schedule.DelayedTaskScheduler;
import io.fluxion.server.infrastructure.schedule.task.DelayedTaskFactory;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import java.time.LocalDateTime;

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

    @CommandHandler
    public ExecutionCreateCmd.Response handle(ExecutionCreateCmd cmd) {
        Executable executable = Query.query(new ExecutableByIdQuery(
            cmd.getRefId(), cmd.getRefType()
        )).getExecutable();
        if (executable == null) {
            throw new PlatformException(ErrorCode.PARAM_ERROR, "executable not found by refId:" + cmd.getRefId() + " refT");
        }
        // 判断是否已经创建
        ExecutionEntity entity = executionEntityRepo.findByRefIdAndRefTypeAndTriggerAt(cmd.getRefId(), cmd.getRefType().value, cmd.getTriggerAt());
        if (entity == null) {
            entity = new ExecutionEntity();
            entity.setExecutionId(Cmd.send(new IDGenerateCmd(IDType.EXECUTION)).getId());
            entity.setScheduleId(cmd.getScheduleId());
            entity.setRefId(cmd.getRefId());
            entity.setRefType(cmd.getRefType().value);
            entity.setTriggerAt(cmd.getTriggerAt());
            entity.setStatus(ExecutionStatus.CREATED.value);
            executionEntityRepo.saveAndFlush(entity);
        }
        Execution execution = new Execution(entity.getExecutionId(), executable, ExecutionStatus.parse(entity.getStatus()));
        return new ExecutionCreateCmd.Response(execution);
    }

    @CommandHandler
    public boolean handle(ExecutionSuccessCmd cmd) {
        boolean finished = updateToFinish(cmd.getExecutionId(), ExecutionStatus.SUCCEED, cmd.getEndAt());
        if (!finished) {
            log.warn("ExecutionSuccessCmd update fail executionId:{}", cmd.getExecutionId());
            return false;
        }
        afterFinsh(cmd.getExecutionId(), cmd.getEndAt());
        return true;
    }

    @CommandHandler
    public boolean handle(ExecutionFailCmd cmd) {
        boolean finished = updateToFinish(cmd.getExecutionId(), ExecutionStatus.FAILED, cmd.getEndAt());
        if (!finished) {
            log.warn("ExecutionFailCmd update fail executionId:{}", cmd.getExecutionId());
            return false;
        }
        afterFinsh(cmd.getExecutionId(), cmd.getEndAt());
        return true;
    }

    private boolean updateToFinish(String executionId, ExecutionStatus status, LocalDateTime endTime) {
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

    private void afterFinsh(String executionId, LocalDateTime feedbackTime) {
        ExecutionEntity entity = executionEntityRepo.findById(executionId).get();
        Schedule schedule = Query.query(new ScheduleByIdQuery(entity.getScheduleId())).getSchedule();
        if (ScheduleType.FIXED_DELAY != schedule.getScheduleType()) {
            return;
        }
        // 更新反馈时间
        Cmd.send(new ScheduleRefreshLastFeedbackCmd(
            schedule.getId(), feedbackTime
        ));
        // FIXED_DELAY 类型的这个时候下发后续的
        DelayedTaskScheduler delayedTaskScheduler = BrokerContext.broker().delayedTaskScheduler();
        delayedTaskScheduler.schedule(DelayedTaskFactory.create(
            TriggerHelper.taskScheduleId(schedule),
            schedule.getLastTriggerAt(), feedbackTime, schedule.getScheduleOption(),
            delayedTask -> TriggerHelper.consumerTask(delayedTask, schedule.getId())
        ));
    }

}
