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
import io.fluxion.server.core.execution.cmd.ExecutableFailCmd;
import io.fluxion.server.core.execution.cmd.ExecutableSuccessCmd;
import io.fluxion.server.core.execution.cmd.ExecutionFailCmd;
import io.fluxion.server.core.execution.query.ExecutionByIdQuery;
import io.fluxion.server.core.executor.option.RetryOption;
import io.fluxion.server.core.task.cmd.TaskFailCmd;
import io.fluxion.server.core.task.cmd.TaskRetryCmd;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.dao.entity.TaskEntity;
import io.fluxion.server.infrastructure.dao.repository.TaskEntityRepo;
import io.fluxion.server.infrastructure.exception.ErrorCode;
import io.fluxion.server.infrastructure.exception.PlatformException;
import io.fluxion.server.infrastructure.lock.DistributedLock;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * @author Devil
 */
@Slf4j
@Service
public class ExecutableCommandService {

    @Resource
    private TaskEntityRepo taskEntityRepo;

    @Resource
    private DistributedLock distributedLock;

    private static final String LOCK_SUFFIX = "_Execution_LOCK";

    @CommandHandler
    public boolean handle(ExecutableSuccessCmd cmd) {
        TaskEntity task = taskEntityRepo.findById(cmd.getTaskId()).orElse(null);
        if (task == null) {
            throw new PlatformException(ErrorCode.PARAM_ERROR, "task not found io:" + cmd.getTaskId());
        }
        Execution execution = Query.query(new ExecutionByIdQuery(task.getExecutionId())).getExecution();
        Executable executable = execution.getExecutable();
        boolean locked = false;
        String lockName = execution.getId() + LOCK_SUFFIX;
        try {
            distributedLock.lock(lockName, 2000, 3000);
            locked = true;
            return executable.success(task.getRefId(), task.getTaskId(), task.getExecutionId(), cmd.getReportAt());
        } finally {
            if (locked) {
                distributedLock.unlock(lockName);
            }
        }
    }

    @CommandHandler
    public boolean handle(ExecutableFailCmd cmd) {
        TaskEntity task = taskEntityRepo.findById(cmd.getTaskId()).orElse(null);
        if (task == null) {
            throw new PlatformException(ErrorCode.PARAM_ERROR, "task not found io:" + cmd.getTaskId());
        }
        Execution execution = Query.query(new ExecutionByIdQuery(task.getExecutionId())).getExecution();
        Executable executable = execution.getExecutable();
        LocalDateTime time = cmd.getReportAt();
        RetryOption retryOption = Optional.ofNullable(executable.retryOption(task.getRefId())).orElse(new RetryOption());
        boolean locked = false;
        String lockName = execution.getId() + LOCK_SUFFIX;
        try {
            distributedLock.lock(lockName, 2000, 3000);
            locked = true;
            if (retryOption.canRetry(task.getRetryTimes())) {
                return Cmd.send(new TaskRetryCmd());
            } else if (executable.skipWhenFail(task.getRefId())) {
                return executable.success(task.getRefId(), task.getTaskId(), task.getExecutionId(), time);
            } else {
                boolean failed = Cmd.send(new TaskFailCmd(task.getTaskId(), time, cmd.getErrorMsg()));
                if (!failed) {
                    return false;
                }
                return Cmd.send(new ExecutionFailCmd(task.getExecutionId(), time));
            }
        } finally {
            if (locked) {
                distributedLock.unlock(lockName);
            }
        }
    }

}
