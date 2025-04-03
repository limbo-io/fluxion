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

import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.server.core.execution.cmd.ExecutionRunningCmd;
import io.fluxion.server.core.job.Job;
import io.fluxion.server.core.job.JobStatus;
import io.fluxion.server.core.job.cmd.JobDispatchedCmd;
import io.fluxion.server.core.job.cmd.JobFinishCmd;
import io.fluxion.server.core.job.cmd.JobReportCmd;
import io.fluxion.server.core.job.cmd.JobRunCmd;
import io.fluxion.server.core.job.cmd.JobStartCmd;
import io.fluxion.server.core.job.cmd.JobsCreateCmd;
import io.fluxion.server.core.job.runner.JobRunner;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.dao.entity.JobEntity;
import io.fluxion.server.infrastructure.dao.repository.JobEntityRepo;
import io.fluxion.server.infrastructure.id.cmd.IDGenerateCmd;
import io.fluxion.server.infrastructure.id.data.IDType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
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
    private List<JobRunner> jobRunners;

    @Resource
    private EntityManager entityManager;

    @Transactional
    @CommandHandler
    public void handle(JobsCreateCmd cmd) {
        List<Job> jobs = new ArrayList<>();
        for (Job job : cmd.getJobs()) {
            // 判断是否已经创建
            JobEntity entity = jobEntityRepo.findByExecutionIdAndRefIdAndJobType(job.getExecutionId(), job.getRefId(), job.type().value);
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
            JobEntity entity = new JobEntity();
            entity.setJobId(job.getJobId());
            entity.setExecutionId(job.getExecutionId());
            entity.setTriggerAt(job.getTriggerAt());
            entity.setStatus(job.getStatus().value);
            entity.setJobType(job.type().value);
            entity.setRefId(job.getRefId());
            entity.setRetryTimes(job.getRetryTimes());
            return entity;
        }).collect(Collectors.toList());
        jobEntityRepo.saveAllAndFlush(entities);
    }

    @CommandHandler
    public void handle(JobRunCmd cmd) {
        Job job = cmd.getJob();
        JobRunner jobRunner = jobRunners.stream().filter(r -> r.type() == job.type()).findFirst().orElse(null);
        if (jobRunner == null) {
            log.warn("[JobRunCmd] can't find type:{}", job.type());
            return;
        }
        jobRunner.run(job);
    }

    @Transactional
    @CommandHandler
    public boolean handle(JobDispatchedCmd cmd) {
        JobEntity entity = jobEntityRepo.findById(cmd.getJobId()).orElse(null);
        if (entity == null) {
            return false;
        }
        int updated = entityManager.createQuery("update JobEntity " +
                "set status = :newStatus, workerAddress = :workerAddress, lastReportAt = :lastReportAt " +
                "where jobId = :jobId and status = :oldStatus"
            )
            .setParameter("newStatus", JobStatus.DISPATCHED.value)
            .setParameter("lastReportAt", TimeUtils.currentLocalDateTime())
            .setParameter("jobId", cmd.getJobId())
            .setParameter("oldStatus", JobStatus.CREATED.value)
            .setParameter("workerAddress", cmd.getWorkerAddress())
            .executeUpdate();
        Cmd.send(new ExecutionRunningCmd(entity.getExecutionId()));
        return updated > 0;
    }

    @Transactional
    @CommandHandler
    public boolean handle(JobStartCmd cmd) {
        StringBuilder sqlSb = new StringBuilder("update JobEntity ");
        sqlSb.append("set status = :newStatus, startAt = :startAt, lastReportAt = :lastReportAt ");
        sqlSb.append("where jobId = :jobId and status = :oldStatus ");
        if (StringUtils.isNotBlank(cmd.getWorkerAddress())) {
            sqlSb.append("and workerAddress = :workerAddress ");
        }

        Query query = entityManager.createQuery(sqlSb.toString())
            .setParameter("startAt", cmd.getReportAt())
            .setParameter("lastReportAt", cmd.getReportAt())
            .setParameter("jobId", cmd.getJobId());
        if (StringUtils.isBlank(cmd.getWorkerAddress())) {
            query.setParameter("newStatus", JobStatus.RUNNING.value)
                .setParameter("oldStatus", JobStatus.CREATED.value);
        } else {
            query.setParameter("newStatus", JobStatus.RUNNING.value)
                .setParameter("oldStatus", JobStatus.DISPATCHED.value)
                .setParameter("workerAddress", cmd.getWorkerAddress());
        }

        int updated = query.executeUpdate();
        JobEntity entity = jobEntityRepo.findById(cmd.getJobId()).orElse(null);
        Cmd.send(new ExecutionRunningCmd(entity.getExecutionId()));
        return updated > 0;
    }

    @Transactional
    @CommandHandler
    public boolean handle(JobReportCmd cmd) {
        int updated = entityManager.createQuery("update JobEntity " +
                "set lastReportAt = :lastReportAt " +
                "where jobId = :jobId and status = :oldStatus and lastReportAt < :lastReportAt " +
                "and workerAddress = :workerAddress"
            )
            .setParameter("lastReportAt", cmd.getReportAt())
            .setParameter("jobId", cmd.getJobId())
            .setParameter("oldStatus", JobStatus.RUNNING.value)
            .setParameter("workerAddress", cmd.getWorkerAddress())
            .executeUpdate();
        return updated > 0;
    }

    @Transactional
    @CommandHandler
    public boolean handle(JobFinishCmd cmd) {
        JobEntity entity = jobEntityRepo.findById(cmd.getJobId()).orElse(null);
        if (entity == null) {
            log.warn("JobFinishCmd not found jobId:{}", cmd.getJobId());
            return false;
        }
        int updated = entityManager.createQuery("update JobEntity " +
                "set lastReportAt = :lastReportAt, status = :newStatus, startAt = :startAt, endAt = :endAt, errorMsg = :errorMsg " +
                "where jobId = :jobId and status = :oldStatus "
            )
            .setParameter("lastReportAt", cmd.getReportAt())
            .setParameter("startAt", entity.getStartAt() == null ? cmd.getReportAt() : entity.getStartAt())
            .setParameter("endAt", cmd.getReportAt())
            .setParameter("jobId", cmd.getJobId())
            .setParameter("oldStatus", cmd.getOldStatus().value)
            .setParameter("newStatus", cmd.getNewStatus().value)
            .setParameter("errorMsg", cmd.getErrorMsg())
            .executeUpdate();
        if (updated <= 0) {
            log.warn("JobFinishCmd update fail jobId:{}", cmd.getJobId());
            return false;
        }
        return true;
    }

}
