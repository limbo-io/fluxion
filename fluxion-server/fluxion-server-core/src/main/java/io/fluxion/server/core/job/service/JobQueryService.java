/*
 * Copyright 2025-2030 limbo-io Team (https://github.com/limbo-io).
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

package io.fluxion.server.core.job.service;

import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.server.core.execution.Executable;
import io.fluxion.server.core.execution.Execution;
import io.fluxion.server.core.execution.query.ExecutionByIdQuery;
import io.fluxion.server.core.job.Job;
import io.fluxion.server.core.job.TaskMonitor;
import io.fluxion.server.core.job.query.JobByIdQuery;
import io.fluxion.server.core.job.query.JobCountByStatusQuery;
import io.fluxion.server.core.job.query.JobInitBlockedQuery;
import io.fluxion.server.core.job.query.JobUnReportQuery;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.dao.entity.JobEntity;
import io.fluxion.server.infrastructure.dao.repository.JobEntityRepo;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
@Service
public class JobQueryService {

    @Resource
    private JobEntityRepo jobEntityRepo;

    @QueryHandler
    public JobCountByStatusQuery.Response handle(JobCountByStatusQuery query) {
        long count = jobEntityRepo.countByExecutionIdAndRefIdInAndStatusIn(
            query.getExecutionId(), query.getRefIds(), query.getStatuses().stream().map(s -> s.value).collect(Collectors.toList())
        );
        return new JobCountByStatusQuery.Response(count);
    }

    @QueryHandler
    public JobByIdQuery.Response handle(JobByIdQuery query) {
        JobEntity entity = jobEntityRepo.findById(query.getJobId()).orElse(null);
        if (entity == null) {
            return new JobByIdQuery.Response(null);
        }
        Execution execution = Query.query(new ExecutionByIdQuery(entity.getExecutionId())).getExecution();
        Executable executable = execution.getExecutable();
        Job job = executable.newRefJob(entity.getRefId());
        job.setJobId(entity.getJobId());
        job.setExecution(execution);
        job.setTriggerAt(entity.getTriggerAt());
        job.setRetryTimes(entity.getRetryTimes());
        job.setTaskMonitor(JacksonUtils.toType(entity.getMonitor(), TaskMonitor.class));
        job.setErrorMsg(entity.getErrorMsg());
        job.setResult(entity.getResult());
        return new JobByIdQuery.Response(job);
    }

    @QueryHandler
    public JobInitBlockedQuery.Response handle(JobInitBlockedQuery query) {

    }

    @QueryHandler
    public JobUnReportQuery.Response handle(JobUnReportQuery query) {

    }

}
