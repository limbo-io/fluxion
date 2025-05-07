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

import io.fluxion.server.core.execution.cmd.ExecutableFailCmd;
import io.fluxion.server.core.execution.cmd.ExecutableSuccessCmd;
import io.fluxion.server.core.job.Job;
import io.fluxion.server.core.job.cmd.JobSuccessCmd;
import io.fluxion.server.core.job.query.JobByIdQuery;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.dao.tx.TransactionService;
import io.fluxion.server.infrastructure.exception.ErrorCode;
import io.fluxion.server.infrastructure.exception.PlatformException;
import io.fluxion.server.infrastructure.lock.DistributedLock;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @author Devil
 */
@Slf4j
@Service
public class ExecutableCommandService {

    @Resource
    private DistributedLock distributedLock;

    @Resource
    private TransactionService transactionService;

    private static final String LOCK_SUFFIX = "_Execution_Lock";

    @CommandHandler
    public boolean handle(ExecutableSuccessCmd cmd) {
        Job job = Query.query(new JobByIdQuery(cmd.getJobId())).getJob();
        if (job == null) {
            throw new PlatformException(ErrorCode.PARAM_ERROR, "job not found id:" + cmd.getJobId());
        }
        String lockName = job.getExecutionId() + LOCK_SUFFIX;
        return distributedLock.lock(lockName, 2000, 3000,
            () -> transactionService.transactional(() -> job.success(cmd.getReportAt()))
        );
    }

    @CommandHandler
    public boolean handle(ExecutableFailCmd cmd) {
        Job job = Query.query(new JobByIdQuery(cmd.getJobId())).getJob();
        if (job == null) {
            throw new PlatformException(ErrorCode.PARAM_ERROR, "job not found id:" + cmd.getJobId());
        }
        job.setJobMonitor(cmd.getMonitor());
        job.setErrorMsg(cmd.getErrorMsg());

        String lockName = job.getExecutionId() + LOCK_SUFFIX;
        try {
            return distributedLock.lock(lockName, 2000, 3000,
                () -> transactionService.transactional(() -> job.fail(cmd.getReportAt()))
            );
        } catch (Exception e) {
            log.error("ExecutableFailCmd fail jobId:{}", cmd.getJobId(), e);
            return false;
        }
    }

}
