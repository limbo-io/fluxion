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
import io.fluxion.server.core.execution.cmd.ExecutableFeedbackCmd;
import io.fluxion.server.core.execution.cmd.ExecutionFailCmd;
import io.fluxion.server.core.execution.query.ExecutionByIdQuery;
import io.fluxion.server.core.executor.option.RetryOption;
import io.fluxion.server.core.job.JobStatus;
import io.fluxion.server.core.job.cmd.JobFinishCmd;
import io.fluxion.server.core.job.cmd.JobRetryCmd;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.dao.entity.JobEntity;
import io.fluxion.server.infrastructure.dao.repository.JobEntityRepo;
import io.fluxion.server.infrastructure.dao.tx.TransactionService;
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
    private JobEntityRepo jobEntityRepo;

    @Resource
    private DistributedLock distributedLock;

    @Resource
    private TransactionService transactionService;

    private static final String LOCK_SUFFIX = "_Execution_Lock";

    @CommandHandler
    public boolean handle(ExecutableFeedbackCmd cmd) {
        JobEntity entity = jobEntityRepo.findById(cmd.getJobId())
            .orElseThrow(PlatformException.supplier(ErrorCode.PARAM_ERROR, "task not found io:" + cmd.getJobId()));
        Execution execution = Query.query(new ExecutionByIdQuery(entity.getExecutionId())).getExecution();
        String lockName = execution.getId() + LOCK_SUFFIX;
        return distributedLock.lock(lockName, 2000, 3000,
            () -> transactionService.transactional(() -> finish(execution, entity, cmd))
        );
    }

    private boolean finish(Execution execution, JobEntity entity, ExecutableFeedbackCmd cmd) {
        Executable executable = execution.getExecutable();
        String jobId = cmd.getJobId();
        LocalDateTime reportAt = cmd.getReportAt();
        switch (cmd.getExecuteResult()) {
            case SUCCEED:
                boolean success = Cmd.send(new JobFinishCmd(jobId, reportAt, JobStatus.RUNNING, JobStatus.SUCCEED));
                if (!success) {
                    return false;
                }
                return executable.success(entity.getRefId(), entity.getJobId(), cmd.getReportAt());
            case PARTIALLY_SUCCESSFUL:
                boolean partSuccess = Cmd.send(new JobFinishCmd(jobId, reportAt, JobStatus.RUNNING, JobStatus.PARTIALLY_SUCCESSFUL));
                if (!partSuccess) {
                    return false;
                }
                return executable.success(entity.getRefId(), entity.getJobId(), cmd.getReportAt());
            case FAILED:
                RetryOption retryOption = Optional.ofNullable(executable.retryOption(entity.getRefId())).orElse(new RetryOption());
                if (retryOption.canRetry(entity.getRetryTimes())) {
                    return Cmd.send(new JobRetryCmd());
                } else if (executable.skipWhenFail(entity.getRefId())) {
                    return executable.success(entity.getRefId(), entity.getExecutionId(), reportAt);
                } else {
                    boolean failed = Cmd.send(new JobFinishCmd(jobId, reportAt, JobStatus.RUNNING, JobStatus.FAILED, cmd.getErrorMsg()));
                    if (!failed) {
                        return false;
                    }
                    return Cmd.send(new ExecutionFailCmd(entity.getExecutionId(), reportAt));
                }
            case TERMINATED:
                throw new UnsupportedOperationException("Manual task termination is not currently supported!");
            case UNKNOWN:
                throw new UnsupportedOperationException("Unknown result type!");
        }
        return false;
    }

}
