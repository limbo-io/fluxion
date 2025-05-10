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
import io.fluxion.remote.core.api.Response;
import io.fluxion.remote.core.api.request.worker.JobDispatchRequest;
import io.fluxion.remote.core.constants.WorkerRemoteConstant;
import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.job.Job;
import io.fluxion.server.core.job.JobType;
import io.fluxion.server.core.job.cmd.JobFailCmd;
import io.fluxion.server.core.job.config.ExecutorJobConfig;
import io.fluxion.server.core.job.query.JobConfigQuery;
import io.fluxion.server.core.worker.Worker;
import io.fluxion.server.core.worker.query.WorkersFilterQuery;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Query;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
@Component
public class ExecutorJobRunner extends JobRunner {

    private static final int MAX_DISPATCH_FAILED_TIMES = 3;

    @Override
    public JobType type() {
        return JobType.EXECUTOR;
    }

    @Override
    public void run(Job job) {
        ExecutorJobConfig config = (ExecutorJobConfig) Query.query(new JobConfigQuery(job.getExecutionId(), job.getRefId())).getConfig();
        List<Worker> workers = Query.query(new WorkersFilterQuery(
            config.getAppId(), config.getExecutorName(),
            config.getDispatchOption(), true, true
        )).getWorkers();
        workers = CollectionUtils.isEmpty(workers) ? Collections.emptyList() : workers.stream().filter(Objects::nonNull).collect(Collectors.toList());
        int dispatchFailedCount = 0;
        boolean dispatched = false;
        Worker worker = null;
        while (dispatchFailedCount < MAX_DISPATCH_FAILED_TIMES) {
            worker = workers.stream().findAny().orElse(null);
            if (worker != null) {
                // 远程调用处理任务
                JobDispatchRequest request = new JobDispatchRequest();
                request.setJobId(job.getJobId());
                request.setExecutorName(config.getExecutorName());
                request.setExecuteMode(config.getExecuteMode().mode);
                // call
                Response<Boolean> dispatchRes = BrokerContext.call(
                    WorkerRemoteConstant.API_JOB_DISPATCH, worker.getHost(), worker.getPort(), request
                );
                dispatched = dispatchRes.success() && BooleanUtils.isTrue(dispatchRes.getData());
            }
            if (dispatched) {
                break;
            }
            // 下发失败
            dispatchFailedCount++;
        }
        if (!dispatched) {
            Cmd.send(new JobFailCmd(
                job.getJobId(),
                TimeUtils.currentLocalDateTime(),
                "dispatch fail worker:" + (worker == null ? null : worker.id()),
                null
            ));
        }
    }


}
