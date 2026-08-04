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
import io.fluxion.server.core.job.query.JobRetryDueQuery;
import io.fluxion.server.core.job.query.JobRunningByWorkerQuery;
import io.fluxion.server.core.job.query.JobTimeoutDueQuery;
import io.fluxion.server.core.job.query.JobExpiredLeaseQuery;
import io.fluxion.server.core.job.query.JobLeaseOwnedQuery;
import io.limbo.cqrs.spring.query.Query;
import io.fluxion.server.infrastructure.dao.entity.JobEntity;
import io.fluxion.server.infrastructure.dao.repository.JobEntityRepo;
import io.limbo.utils.json.JacksonUtils;
import io.limbo.cqrs.spring.annotation.QueryHandler;
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

    @QueryHandler
    public JobRetryDueQuery.Response handle(JobRetryDueQuery query) {
        String brokerId = BrokerContext.broker().id();
        List<Integer> buckets = Query.query(new BucketsByBrokerQuery(brokerId)).getBuckets();
        if (buckets.isEmpty()) {
            return new JobRetryDueQuery.Response(java.util.Collections.emptyList());
        }
        List<JobRetryDueQuery.JobRetry> jobs = entityManager.createQuery("select e.jobId, e.retryTimes from JobEntity e "
                + "where e.bucket in :buckets and e.status = :status and e.nextRetryAt <= :now "
                + "order by e.nextRetryAt asc", Object[].class)
            .setParameter("buckets", buckets)
            .setParameter("status", JobStatus.RETRY_WAIT.value)
            .setParameter("now", query.getNow())
            .setMaxResults(query.getLimit())
            .getResultList().stream()
            .map(row -> new JobRetryDueQuery.JobRetry((String) row[0], (Integer) row[1]))
            .collect(Collectors.toList());
        return new JobRetryDueQuery.Response(jobs);
    }

    @QueryHandler
    public JobRunningByWorkerQuery.Response handle(JobRunningByWorkerQuery query) {
        String brokerId = BrokerContext.broker().id();
        List<Integer> buckets = Query.query(new BucketsByBrokerQuery(brokerId)).getBuckets();
        if (buckets.isEmpty()) {
            return new JobRunningByWorkerQuery.Response(java.util.Collections.emptyList());
        }
        List<JobRunningByWorkerQuery.JobRunning> jobs = entityManager.createQuery("select e.jobId, e.dispatchAttempt from JobEntity e "
                + "where e.bucket in :buckets and e.status = :status and e.workerAddress = :workerAddress", Object[].class)
            .setParameter("buckets", buckets)
            .setParameter("status", JobStatus.RUNNING.value)
            .setParameter("workerAddress", query.getWorkerAddress())
            .setMaxResults(query.getLimit())
            .getResultList().stream()
            .map(row -> new JobRunningByWorkerQuery.JobRunning((String) row[0], (Integer) row[1]))
            .collect(Collectors.toList());
        return new JobRunningByWorkerQuery.Response(jobs);
    }

    @QueryHandler
    public JobTimeoutDueQuery.Response handle(JobTimeoutDueQuery query) {
        String brokerId = BrokerContext.broker().id();
        List<Integer> buckets = Query.query(new BucketsByBrokerQuery(brokerId)).getBuckets();
        if (buckets.isEmpty()) {
            return new JobTimeoutDueQuery.Response(java.util.Collections.emptyList());
        }
        List<JobTimeoutDueQuery.JobTimeout> jobs = entityManager.createQuery("select e.jobId, e.dispatchAttempt from JobEntity e "
                + "where e.bucket in :buckets and e.status = :status and e.timeoutAt <= :now", Object[].class)
            .setParameter("buckets", buckets)
            .setParameter("status", JobStatus.RUNNING.value)
            .setParameter("now", query.getNow())
            .setMaxResults(query.getLimit())
            .getResultList().stream()
            .map(row -> new JobTimeoutDueQuery.JobTimeout((String) row[0], (Integer) row[1]))
            .collect(Collectors.toList());
        return new JobTimeoutDueQuery.Response(jobs);
    }

    @QueryHandler
    public JobExpiredLeaseQuery.Response handle(JobExpiredLeaseQuery query) {
        String brokerId = BrokerContext.broker().id();
        List<Integer> buckets = Query.query(new BucketsByBrokerQuery(brokerId)).getBuckets();
        if (buckets.isEmpty()) {
            return new JobExpiredLeaseQuery.Response(java.util.Collections.emptyList());
        }
        List<JobExpiredLeaseQuery.JobLease> jobs = entityManager.createQuery("select e.jobId, e.dispatchAttempt from JobEntity e "
                + "where e.bucket in :buckets and e.status = :status and e.leaseUntil < :now", Object[].class)
            .setParameter("buckets", buckets)
            .setParameter("status", JobStatus.RUNNING.value)
            .setParameter("now", query.getNow())
            .setMaxResults(query.getLimit())
            .getResultList().stream()
            .map(row -> new JobExpiredLeaseQuery.JobLease((String) row[0], (Integer) row[1]))
            .collect(Collectors.toList());
        return new JobExpiredLeaseQuery.Response(jobs);
    }

    @QueryHandler
    public JobLeaseOwnedQuery.Response handle(JobLeaseOwnedQuery query) {
        List<JobLeaseOwnedQuery.JobLease> jobs = entityManager.createQuery("select e.jobId, e.dispatchAttempt from JobEntity e "
                + "where e.deleted = false and e.leaseOwner = :brokerId and e.leaseUntil > :now and e.status in :statuses and e.jobId > :lastJobId "
                + "order by e.jobId asc", Object[].class)
            .setParameter("brokerId", query.getBrokerId())
            .setParameter("now", java.time.LocalDateTime.now())
            .setParameter("statuses", java.util.Arrays.asList(JobStatus.INITED.value, JobStatus.RUNNING.value))
            .setParameter("lastJobId", query.getLastJobId())
            .setMaxResults(query.getLimit())
            .getResultList().stream()
            .map(row -> new JobLeaseOwnedQuery.JobLease((String) row[0], (Integer) row[1]))
            .collect(Collectors.toList());
        return new JobLeaseOwnedQuery.Response(jobs);
    }

    @QueryHandler
    public JobConfigQuery.Response handle(JobConfigQuery query) {
        Execution execution = Query.query(new ExecutionByIdQuery(query.getExecutionId())).getExecution();
        Executable executable = execution.executable();
        Job.Config config = executable.config(query.getRefId());
        return new JobConfigQuery.Response(config);
    }

}
