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

package io.fluxion.server.core.job.runner;

import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.remote.core.api.request.JobDispatchRequest;
import io.fluxion.remote.core.constants.WorkerRemoteConstant;
import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.execution.cmd.ExecutableFailCmd;
import io.fluxion.server.core.job.ExecutorJob;
import io.fluxion.server.core.job.Job;
import io.fluxion.server.core.job.JobType;
import io.fluxion.server.core.worker.Worker;
import io.fluxion.server.core.worker.query.WorkersFilterQuery;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Query;
import org.springframework.stereotype.Component;

/**
 * @author Devil
 */
@Component
public class ExecutorJobRunner extends JobRunner {

    @Override
    public JobType type() {
        return JobType.EXECUTOR;
    }

    @Override
    public void run(Job job) {
        ExecutorJob executorJob = (ExecutorJob) job;
        Worker worker = Query.query(new WorkersFilterQuery(
            executorJob.getAppId(), executorJob.getExecutorName(),
            executorJob.getDispatchOption(), true, true
        )).getWorkers().stream().findAny().orElse(null);
        boolean dispatched = false;
        if (worker != null) {
            // 远程调用处理任务
            JobDispatchRequest request = new JobDispatchRequest();
            request.setJobId(job.getJobId());
            request.setExecutorName(executorJob.getExecutorName());
            request.setExecuteMode(executorJob.getExecuteMode().mode);
            // call
            dispatched = BrokerContext.call(
                WorkerRemoteConstant.API_JOB_DISPATCH, worker.getHost(), worker.getPort(), request
            );
        }

        String workerAddress = worker == null ? null : worker.getAddress();
        if (!dispatched) {
            Cmd.send(new ExecutableFailCmd(
                job.getJobId(),
                TimeUtils.currentLocalDateTime(),
                "dispatch fail worker:" + workerAddress
            ));
        }
    }


}
