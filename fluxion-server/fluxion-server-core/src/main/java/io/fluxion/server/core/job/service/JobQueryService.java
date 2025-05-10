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
import io.fluxion.remote.core.constants.JobStatus;
import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.broker.query.BucketsByBrokerQuery;
import io.fluxion.server.core.execution.Executable;
import io.fluxion.server.core.execution.Execution;
import io.fluxion.server.core.execution.query.ExecutionByIdQuery;
import io.fluxion.server.core.job.Job;
import io.fluxion.server.core.job.JobType;
import io.fluxion.server.core.job.JobMonitor;
import io.fluxion.server.core.job.query.JobByIdQuery;
import io.fluxion.server.core.job.query.JobConfigQuery;
import io.fluxion.server.core.job.query.JobCountByStatusQuery;
import io.fluxion.server.core.job.query.JobInitBlockedQuery;
import io.fluxion.server.core.job.query.JobUnReportQuery;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.dao.entity.JobEntity;
import io.fluxion.server.infrastructure.dao.repository.JobEntityRepo;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
@Service
public class JobQueryService {

    @Resource
    private JobEntityRepo jobEntityRepo;

    @Resource
    private EntityManager entityManager;

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
        Job job = new Job();
        job.setType(JobType.parse(entity.getJobType()));
        job.setJobId(entity.getJobId());
        job.setRefId(entity.getRefId());
        job.setExecutionId(entity.getExecutionId());
        job.setTriggerAt(entity.getTriggerAt());
        job.setRetryTimes(entity.getRetryTimes());
        job.setJobMonitor(JacksonUtils.toType(entity.getMonitor(), JobMonitor.class));
        job.setResult(entity.getResult());
        return new JobByIdQuery.Response(job);
    }

    /**
     * 超过触发时间一段时间还是init状态的
     */
    @QueryHandler
    public JobInitBlockedQuery.Response handle(JobInitBlockedQuery query) {
        String brokerId = BrokerContext.broker().id();
        List<Integer> buckets = Query.query(new BucketsByBrokerQuery(brokerId)).getBuckets();
        List<JobEntity> entities = entityManager.createQuery("select e from JobEntity e" +
                " where e.bucket in :buckets and e.triggerAt <= :triggerAt and status = :status and jobId > :lastId " +
                " order by jobId asc ", JobEntity.class
            )
            .setParameter("buckets", buckets)
            .setParameter("status", JobStatus.INITED.value)
            .setParameter("lastId", query.getLastJobId())
            .setParameter("triggerAt", query.getEndAt())
            .setMaxResults(query.getLimit())
            .getResultList();
        return new JobInitBlockedQuery.Response(entities.stream().map(JobEntity::getJobId).collect(Collectors.toList()));
    }

    /**
     * 运行中且上报时间超过一段时间了
     */
    @QueryHandler
    public JobUnReportQuery.Response handle(JobUnReportQuery query) {
        String brokerId = BrokerContext.broker().id();
        List<Integer> buckets = Query.query(new BucketsByBrokerQuery(brokerId)).getBuckets();
        List<JobEntity> entities = entityManager.createQuery("select e from JobEntity e" +
                " where e.bucket in :buckets and e.lastReportAt <= :lastReportAt and status =:status and jobId > :lastId " +
                " order by jobId asc ", JobEntity.class
            )
            .setParameter("buckets", buckets)
            .setParameter("status", JobStatus.RUNNING.value)
            .setParameter("lastId", query.getLastJobId())
            .setParameter("lastReportAt", query.getEndAt())
            .setMaxResults(query.getLimit())
            .getResultList();
        return new JobUnReportQuery.Response(entities.stream().map(JobEntity::getJobId).collect(Collectors.toList()));
    }

    @QueryHandler
    public JobConfigQuery.Response handle(JobConfigQuery query) {
        Execution execution = Query.query(new ExecutionByIdQuery(query.getExecutionId())).getExecution();
        Executable executable = execution.executable();
        Job.Config config = executable.config(query.getRefId());
        return new JobConfigQuery.Response(config);
    }

}
