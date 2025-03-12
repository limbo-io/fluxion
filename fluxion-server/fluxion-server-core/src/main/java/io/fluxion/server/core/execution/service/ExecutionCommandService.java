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

import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.server.core.execution.Executable;
import io.fluxion.server.core.execution.ExecuteConfig;
import io.fluxion.server.core.execution.Execution;
import io.fluxion.server.core.execution.ExecutionStatus;
import io.fluxion.server.core.execution.cmd.ExecutionCreateCmd;
import io.fluxion.server.core.execution.cmd.ExecutionFailCmd;
import io.fluxion.server.core.execution.cmd.ExecutionSuccessCmd;
import io.fluxion.server.core.execution.query.ExecutableByIdQuery;
import io.fluxion.server.core.schedule.Schedule;
import io.fluxion.server.core.schedule.cmd.ScheduleFeedbackCmd;
import io.fluxion.server.core.schedule.query.ScheduleByIdQuery;
import io.fluxion.server.core.trigger.TriggerType;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.dao.entity.ExecutionEntity;
import io.fluxion.server.infrastructure.dao.repository.ExecutionEntityRepo;
import io.fluxion.server.infrastructure.exception.ErrorCode;
import io.fluxion.server.infrastructure.exception.PlatformException;
import io.fluxion.server.infrastructure.id.cmd.IDGenerateCmd;
import io.fluxion.server.infrastructure.id.data.IDType;
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
        ExecuteConfig executeConfig = cmd.getExecuteConfig();
        if (executeConfig == null) {
            throw new PlatformException(ErrorCode.PARAM_ERROR, "executeConfig is null");
        }
        Executable executable = Query.query(new ExecutableByIdQuery(
            executeConfig.executeId(), executeConfig.type()
        )).getExecutable();
        if (executable == null) {
            throw new PlatformException(ErrorCode.PARAM_ERROR, "executable not found by config:" + JacksonUtils.toJSONString(executeConfig) + " refT");
        }
        // 判断是否已经创建
        ExecutionEntity entity = executionEntityRepo.findByRefIdAndRefTypeAndTriggerAt(executeConfig.executeId(), executeConfig.getType(), cmd.getTriggerAt());
        if (entity == null) {
            entity = new ExecutionEntity();
            entity.setExecutionId(Cmd.send(new IDGenerateCmd(IDType.EXECUTION)).getId());
            entity.setTriggerId(cmd.getTriggerId());
            entity.setTriggerType(cmd.getTriggerType().value);
            entity.setRefId(executeConfig.executeId());
            entity.setRefType(executeConfig.getType());
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
        afterFinsh(cmd.getExecutionId());
        return true;
    }

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

    private void afterFinsh(String executionId) {
        ExecutionEntity entity = executionEntityRepo.findById(executionId).get();
        if (TriggerType.SCHEDULE != TriggerType.parse(entity.getTriggerType())) {
            return;
        }
        Schedule schedule = Query.query(new ScheduleByIdQuery(entity.getTriggerId())).getSchedule();
        Cmd.send(new ScheduleFeedbackCmd(schedule));
    }

}
