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

package io.fluxion.server.core.job.service;

import com.google.common.collect.Lists;
import io.fluxion.remote.core.constants.JobStatus;
import io.fluxion.server.core.broker.cmd.BucketAllotCmd;
import io.fluxion.server.core.execution.Execution;
import io.fluxion.server.core.execution.cmd.ExecutionRunningCmd;
import io.fluxion.server.core.execution.query.ExecutionByIdQuery;
import io.fluxion.server.core.job.Job;
import io.fluxion.server.core.job.JobMonitor;
import io.fluxion.server.core.job.JobType;
import io.fluxion.server.core.job.cmd.JobFailCmd;
import io.fluxion.server.core.job.cmd.JobLeaseRenewCmd;
import io.fluxion.server.core.job.cmd.JobReportCmd;
import io.fluxion.server.core.job.cmd.JobRetryCmd;
import io.fluxion.server.core.job.cmd.JobRunCmd;
import io.fluxion.server.core.job.cmd.JobStateTransitionCmd;
import io.fluxion.server.core.job.cmd.JobSuccessCmd;
import io.fluxion.server.core.job.cmd.JobsCreateCmd;
import io.fluxion.server.core.job.query.JobConfigQuery;
import io.fluxion.server.core.job.runner.JobRunner;
import io.limbo.cqrs.spring.command.Cmd;
import io.limbo.cqrs.spring.query.Query;
import io.fluxion.server.infrastructure.dao.entity.JobEntity;
import io.fluxion.server.infrastructure.dao.entity.JobRecordEntity;
import io.fluxion.server.infrastructure.dao.repository.JobEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.JobRecordEntityRepo;
import io.fluxion.server.infrastructure.dao.tx.TransactionService;
import io.fluxion.server.infrastructure.exception.ErrorCode;
import io.fluxion.server.infrastructure.exception.PlatformException;
import io.fluxion.server.infrastructure.id.cmd.IDGenerateCmd;
import io.fluxion.server.infrastructure.id.data.IDType;
import io.fluxion.server.infrastructure.lock.DistributedLock;
import io.limbo.utils.json.JacksonUtils;
import io.limbo.utils.time.TimeUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import io.limbo.cqrs.spring.annotation.CommandHandler;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
@Slf4j
@Component
public class JobCommandService {

    @Resource
    private JobEntityRepo jobEntityRepo;

    @Resource
    private JobRecordEntityRepo jobRecordEntityRepo;

    @Resource
    private List<JobRunner> jobRunners;

    @Resource
    private EntityManager entityManager;

    @Resource
    private TransactionService transactionService;

    @Resource
    private DistributedLock distributedLock;

    @Resource
    private JobRetryService jobRetryService;

    @Resource
    private JobLeaseService jobLeaseService;

    private static final String LOCK_SUFFIX = "_Execution_Lock";

    @Transactional
    @CommandHandler
    public void handle(JobsCreateCmd cmd) {
        List<Job> jobs = new ArrayList<>();
        for (Job job : cmd.getJobs()) {
            // 判断是否已经创建
            JobEntity entity = jobEntityRepo.findByExecutionIdAndRefIdAndJobType(job.getExecutionId(), job.getRefId(), job.getType().value);
            if (entity == null) {
                jobs.add(job);
            }
        }
        if (CollectionUtils.isEmpty(jobs)) {
            return;
        }
        List<JobEntity> entities = jobs.stream().peek(job -> {
            String id = Cmd.send(new IDGenerateCmd(IDType.JOB)).getId();
            job.setJobId(id);
        }).map(job -> {
            String executionId = job.getExecutionId();
            int bucket = Cmd.send(new BucketAllotCmd(executionId + "_" + job.getRefId())).getBucket();
            JobEntity entity = new JobEntity();
            entity.setJobId(job.getJobId());
            entity.setExecutionId(executionId);
            entity.setBucket(bucket);
            entity.setTriggerAt(job.getTriggerAt());
            entity.setStatus(job.getStatus().value);
            entity.setJobType(job.getType().value);
            entity.setRefId(job.getRefId());
            entity.setRetryTimes(job.getRetryTimes());
            return entity;
        }).collect(Collectors.toList());
        jobEntityRepo.saveAllAndFlush(entities);
    }

    @CommandHandler
    public void handle(JobRunCmd cmd) {
        Job job = cmd.getJob();
        JobRunner jobRunner = jobRunners.stream().filter(r -> r.type() == job.getType()).findFirst().orElse(null);
        if (jobRunner == null) {
            log.warn("[JobRunCmd] can't find type:{}", job.getType());
            return;
        }
        jobRunner.run(job);
    }

    @CommandHandler
    public JobReportCmd.Response handle(JobReportCmd cmd) {
        String workerAddress = cmd.getWorkerNode().address();
        boolean success = report(cmd.getJobId(), cmd.getDispatchAttempt(), cmd.getStatus(), workerAddress, cmd.getMonitor(), cmd.getReportAt());
        return new JobReportCmd.Response(success);
    }

    @CommandHandler
    public JobStateTransitionCmd.Response handle(JobStateTransitionCmd cmd) {
        JobEntity entity = jobEntityRepo.findById(cmd.getJobId()).orElse(null);
        if (entity == null) {
            log.warn("JobStateTransitionCmd job not found jobId:{} event:{}", cmd.getJobId(), cmd.getEvent());
            return new JobStateTransitionCmd.Response(false);
        }
        String workerAddress = cmd.getWorkerNode().address();
        if (cmd.getDispatchAttempt() != null && !Objects.equals(entity.getDispatchAttempt(), cmd.getDispatchAttempt())) {
            return new JobStateTransitionCmd.Response(false);
        }
        boolean success = false;
        switch (cmd.getEvent()) {
            case START:
                success = start(entity, cmd.getDispatchAttempt(), workerAddress, cmd.getReportAt());
                break;
            case RUN_SUCCESS:
                if (StringUtils.isNotBlank(entity.getWorkerAddress()) && !cmd.getWorkerNode().address().equals(entity.getWorkerAddress())) {
                    return new JobStateTransitionCmd.Response(false);
                }
                success = Cmd.send(new JobSuccessCmd(
                    cmd.getJobId(), cmd.getReportAt(), cmd.getDispatchAttempt(),
                    workerAddress, cmd.getMonitor(), cmd.getResult()
                ));
                break;
            case RUN_FAIL:
                if (StringUtils.isNotBlank(entity.getWorkerAddress()) && !cmd.getWorkerNode().address().equals(entity.getWorkerAddress())) {
                    return new JobStateTransitionCmd.Response(false);
                }
                success = Cmd.send(new JobFailCmd(
                    cmd.getJobId(), cmd.getReportAt(), cmd.getDispatchAttempt(),
                    workerAddress, cmd.getErrorMsg(), cmd.getMonitor()
                ));
                break;
        }
        if (!success && log.isDebugEnabled()) {
            log.warn("JobStateTransition fail jobId:{} event:{}", entity.getJobId(), cmd.getEvent());
        }
        return new JobStateTransitionCmd.Response(success);
    }

    /**
     * 更新上报时间等信息
     */
    private boolean report(String jobId, int dispatchAttempt, JobStatus status, String workerAddress, JobMonitor monitor, LocalDateTime reportAt) {
        return transactionService.transactional(() -> {
            int updated = entityManager.createQuery("update JobEntity " +
                    "set lastReportAt = :lastReportAt, monitor = :monitor, workerAddress = :workerAddress " +
                    "where jobId = :jobId and status = :status and dispatchAttempt = :dispatchAttempt"
                )
                .setParameter("lastReportAt", reportAt)
                .setParameter("jobId", jobId)
                .setParameter("dispatchAttempt", dispatchAttempt)
                .setParameter("status", status.value)
                .setParameter("monitor", JacksonUtils.toJSONString(monitor))
                .setParameter("workerAddress", workerAddress)
                .executeUpdate();
            return updated > 0;
        });
    }

    private boolean start(JobEntity entity, Integer dispatchAttempt, String workerAddress, LocalDateTime reportAt) {
        return transactionService.transactional(() -> {
            String attemptCondition = dispatchAttempt == null ? "" : " and dispatchAttempt = :dispatchAttempt";
            javax.persistence.Query startUpdate = entityManager.createQuery("update JobEntity " +
                    "set status = :newStatus, startAt = :lastReportAt, lastReportAt = :lastReportAt, workerAddress = :workerAddress " +
                    "where jobId = :jobId and status = :oldStatuses" + attemptCondition
                )
                .setParameter("lastReportAt", reportAt)
                .setParameter("jobId", entity.getJobId())
                .setParameter("newStatus", JobStatus.RUNNING.value)
                .setParameter("workerAddress", workerAddress)
                .setParameter("oldStatuses", JobStatus.INITED.value);
            if (dispatchAttempt != null) {
                startUpdate.setParameter("dispatchAttempt", dispatchAttempt);
            }
            int updated = startUpdate.executeUpdate();
            if (updated <= 0) {
                log.warn("JobStart update fail jobId:{}", entity.getJobId());
            }
            Cmd.send(ExecutionRunningCmd.builder().executionId(entity.getExecutionId()).build());
            return updated > 0;
        });
    }

    @Transactional
    @CommandHandler
    public boolean handle(JobSuccessCmd cmd) {
        JobEntity entity = jobEntityRepo.findById(cmd.getJobId()).orElse(null);
        if (entity == null) {
            throw new PlatformException(ErrorCode.PARAM_ERROR, "job not found id:" + cmd.getJobId());
        }

        String attemptCondition = cmd.getDispatchAttempt() == null ? "" : " and dispatchAttempt = :dispatchAttempt ";
        String workerCondition = cmd.getWorkerAddress() == null ? "" : " and workerAddress = :workerAddress ";
        javax.persistence.Query successUpdate = entityManager.createQuery("update JobEntity " +
                "set lastReportAt = :lastReportAt, status = :newStatus, startAt = :startAt, endAt = :endAt, monitor = :monitor, result = :result " +
                "where jobId = :jobId and status = :oldStatus " + attemptCondition + workerCondition
            )
            .setParameter("lastReportAt", cmd.getReportAt())
            .setParameter("startAt", entity.getStartAt() == null ? cmd.getReportAt() : entity.getStartAt())
            .setParameter("endAt", cmd.getReportAt())
            .setParameter("jobId", cmd.getJobId())
            .setParameter("result", cmd.getResult())
            .setParameter("oldStatus", JobStatus.RUNNING.value)
            .setParameter("newStatus", JobStatus.SUCCEED.value)
            .setParameter("monitor", cmd.getMonitor() == null ? "" : JacksonUtils.toJSONString(cmd.getMonitor()));
        if (cmd.getDispatchAttempt() != null) {
            successUpdate.setParameter("dispatchAttempt", cmd.getDispatchAttempt());
        }
        if (cmd.getWorkerAddress() != null) {
            successUpdate.setParameter("workerAddress", cmd.getWorkerAddress());
        }
        int updated = successUpdate.executeUpdate();
        if (updated <= 0) {
            log.warn("JobSuccessCmd update fail jobId:{}", cmd.getJobId());
            return false;
        }
        // 保存记录
        JobRecordEntity recordEntity = new JobRecordEntity();
        recordEntity.setId(new JobRecordEntity.ID(
            cmd.getJobId(), entity.getRetryTimes()
        ));
        recordEntity.setStartAt(entity.getStartAt());
        recordEntity.setEndAt(cmd.getReportAt());
        recordEntity.setStatus(JobStatus.SUCCEED.value);
        recordEntity.setWorkerAddress(entity.getWorkerAddress());
        recordEntity.setResult(cmd.getResult());
        jobRecordEntityRepo.saveAndFlush(recordEntity);

        String lockName = entity.getExecutionId() + LOCK_SUFFIX;
        return distributedLock.lock(lockName, 2000, 3000, () -> {
            Execution execution = Query.query(new ExecutionByIdQuery(entity.getExecutionId())).getExecution();
            return execution.executable().success(entity.getExecutionId(), entity.getRefId(), cmd.getReportAt());
        });
    }

    @Transactional
    @CommandHandler
    public boolean handle(JobFailCmd cmd) {
        JobEntity entity = jobEntityRepo.findById(cmd.getJobId()).orElse(null);
        if (entity == null) {
            throw new PlatformException(ErrorCode.PARAM_ERROR, "job not found id:" + cmd.getJobId());
        }

        String attemptCondition = cmd.getDispatchAttempt() == null ? "" : " and dispatchAttempt = :dispatchAttempt ";
        String workerCondition = cmd.getWorkerAddress() == null ? "" : " and workerAddress = :workerAddress ";
        javax.persistence.Query failUpdate = entityManager.createQuery("update JobEntity " +
                "set lastReportAt = :lastReportAt, status = :newStatus, startAt = :startAt, endAt = :endAt," +
                " monitor =:monitor " +
                "where jobId = :jobId and status in :oldStatus " + attemptCondition + workerCondition
            )
            .setParameter("lastReportAt", cmd.getReportAt())
            .setParameter("startAt", entity.getStartAt() == null ? cmd.getReportAt() : entity.getStartAt())
            .setParameter("endAt", cmd.getReportAt())
            .setParameter("jobId", cmd.getJobId())
            .setParameter("oldStatus", Lists.newArrayList(
                JobStatus.RUNNING.value, // 运行时失败
                JobStatus.INITED.value // broker下发失败
            ))
            .setParameter("newStatus", JobStatus.FAILED.value)
            .setParameter("monitor", cmd.getMonitor() == null ? "" : JacksonUtils.toJSONString(cmd.getMonitor()));
        if (cmd.getDispatchAttempt() != null) {
            failUpdate.setParameter("dispatchAttempt", cmd.getDispatchAttempt());
        }
        if (cmd.getWorkerAddress() != null) {
            failUpdate.setParameter("workerAddress", cmd.getWorkerAddress());
        }
        int updated = failUpdate.executeUpdate();
        if (updated <= 0) {
            log.warn("JobFailCmd update fail jobId:{}", cmd.getJobId());
            return false;
        }
        // 保存记录
        JobRecordEntity recordEntity = new JobRecordEntity();
        recordEntity.setId(new JobRecordEntity.ID(
            cmd.getJobId(), entity.getRetryTimes()
        ));
        recordEntity.setStartAt(entity.getStartAt());
        recordEntity.setEndAt(cmd.getReportAt());
        recordEntity.setStatus(JobStatus.FAILED.value);
        recordEntity.setWorkerAddress(entity.getWorkerAddress());
        recordEntity.setErrorMsg(cmd.getErrorMsg());
        jobRecordEntityRepo.saveAndFlush(recordEntity);

        // 重试逻辑
        Job.Config config = Query.query(new JobConfigQuery(entity.getExecutionId(), entity.getRefId())).getConfig();
        if (config.getRetryOption().canRetry(entity.getRetryTimes())) {
            LocalDateTime nextRetryAt = TimeUtils.currentLocalDateTime().plusSeconds(config.getRetryOption().getRetryInterval());
            return Cmd.send(new JobRetryCmd(entity.getJobId(), entity.getRetryTimes() + 1, nextRetryAt));
        }
        String lockName = entity.getExecutionId() + LOCK_SUFFIX;
        return distributedLock.lock(lockName, 2000, 3000, () -> {
                Execution execution = Query.query(new ExecutionByIdQuery(entity.getExecutionId())).getExecution();
                return execution.executable().fail(entity.getExecutionId(), entity.getRefId(), cmd.getReportAt());
            }
        );
    }

    @Transactional
    @CommandHandler
    public boolean handle(JobRetryCmd cmd) {
        JobEntity entity = jobEntityRepo.findById(cmd.getJobId()).orElse(null);
        if (entity == null) {
            log.warn("JobRetryCmd job not found jobId:{}", cmd.getJobId());
            return false;
        }

        if (cmd.getNextRetryAt() != null) {
            return jobRetryService.schedule(cmd.getJobId(), entity.getRetryTimes(), cmd.getRetryTimes(), cmd.getNextRetryAt());
        }
        if (!jobRetryService.activate(cmd.getJobId(), cmd.getRetryTimes(), TimeUtils.currentLocalDateTime())) {
            return false;
        }

        Job job = new Job();
        job.setJobId(entity.getJobId());
        job.setExecutionId(entity.getExecutionId());
        job.setRefId(entity.getRefId());
        job.setType(JobType.parse(entity.getJobType()));
        job.setStatus(JobStatus.INITED);
        job.setTriggerAt(entity.getTriggerAt());
        job.setRetryTimes(cmd.getRetryTimes());
        run(job);
        return true;
    }

    @CommandHandler
    public boolean handle(JobLeaseRenewCmd cmd) {
        return jobLeaseService.renew(cmd.getJobId(), cmd.getBrokerId(), cmd.getDispatchAttempt(), cmd.getLeaseUntil());
    }

    protected void run(Job job) {
        Cmd.send(new JobRunCmd(job));
    }

}
