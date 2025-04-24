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
import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.remote.core.constants.JobStatus;
import io.fluxion.server.core.execution.cmd.ExecutableFailCmd;
import io.fluxion.server.core.execution.cmd.ExecutableSuccessCmd;
import io.fluxion.server.core.execution.cmd.ExecutionRunningCmd;
import io.fluxion.server.core.job.Job;
import io.fluxion.server.core.job.TaskMonitor;
import io.fluxion.server.core.job.cmd.JobFailCmd;
import io.fluxion.server.core.job.cmd.JobReportCmd;
import io.fluxion.server.core.job.cmd.JobRunCmd;
import io.fluxion.server.core.job.cmd.JobSuccessCmd;
import io.fluxion.server.core.job.cmd.JobsCreateCmd;
import io.fluxion.server.core.job.runner.JobRunner;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.dao.entity.JobEntity;
import io.fluxion.server.infrastructure.dao.repository.JobEntityRepo;
import io.fluxion.server.infrastructure.dao.tx.TransactionService;
import io.fluxion.server.infrastructure.id.cmd.IDGenerateCmd;
import io.fluxion.server.infrastructure.id.data.IDType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
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

    @Resource
    private TransactionService transactionService;

    @Transactional
    @CommandHandler
    public void handle(JobsCreateCmd cmd) {
        List<Job> jobs = new ArrayList<>();
        for (Job job : cmd.getJobs()) {
            // 判断是否已经创建
            JobEntity entity = jobEntityRepo.findByExecutionIdAndRefIdAndJobType(job.getExecution().getId(), job.getRefId(), job.type().value);
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
            entity.setExecutionId(job.getExecution().getId());
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

    @CommandHandler
    public JobReportCmd.Response handle(JobReportCmd cmd) {
        JobEntity entity = jobEntityRepo.findById(cmd.getJobId()).orElse(null);
        if (entity == null) {
            return new JobReportCmd.Response();
        }
        JobStatus currentStatus = JobStatus.parse(entity.getStatus());
        JobReportCmd.Response response = new JobReportCmd.Response();
        response.setStatus(currentStatus);
        response.setWorkerNode(cmd.getWorkerNode());
        if (StringUtils.isNotBlank(entity.getWorkerAddress()) && !cmd.getWorkerNode().address().equals(entity.getWorkerAddress())) {
            response.setSuccess(false);
            return response;
        }
        String workerAddress = cmd.getWorkerNode().address();
        boolean success = false;
        switch (cmd.getStatus()) {
            case DISPATCHED:
                if (JobStatus.CREATED == JobStatus.parse(entity.getStatus())) {
                    success = dispatched(entity, workerAddress, cmd.getReportAt());
                } else if (JobStatus.DISPATCHED == JobStatus.parse(entity.getStatus())) {
                    success = report(entity, cmd.getMonitor(), cmd.getReportAt());
                }
                break;
            case RUNNING:
                if (JobStatus.CREATED == JobStatus.parse(entity.getStatus()) // broker上执行的不需要下发状态
                    || JobStatus.DISPATCHED == JobStatus.parse(entity.getStatus())) {
                    success = start(entity, workerAddress, cmd.getReportAt());
                } else if (JobStatus.RUNNING == JobStatus.parse(entity.getStatus())) {
                    success = report(entity, cmd.getMonitor(), cmd.getReportAt());
                }
                break;
            case SUCCEED:
                success = Cmd.send(new ExecutableSuccessCmd(
                    cmd.getJobId(), cmd.getReportAt(),
                    cmd.getMonitor(), cmd.getResult()
                ));
                break;
            case FAILED:
                success = Cmd.send(new ExecutableFailCmd(
                    cmd.getJobId(), cmd.getReportAt(),
                    cmd.getMonitor(), cmd.getErrorMsg()
                ));
                break;
        }
        response.setSuccess(success);
        return response;
    }

    /**
     * 状态更新为dispatched
     */
    private boolean dispatched(JobEntity entity, String workerAddress, LocalDateTime reportAt) {
        return transactionService.transactional(() -> {
            int updated = entityManager.createQuery("update JobEntity " +
                    "set status = :newStatus, startAt = :lastReportAt, lastReportAt = :lastReportAt, workerAddress = :workerAddress " +
                    "where jobId = :jobId and status in :oldStatuses"
                )
                .setParameter("lastReportAt", reportAt)
                .setParameter("jobId", entity.getJobId())
                .setParameter("newStatus", JobStatus.RUNNING.value)
                .setParameter("oldStatuses", Lists.newArrayList(JobStatus.DISPATCHED.value, JobStatus.CREATED.value))
                .setParameter("workerAddress", workerAddress)
                .executeUpdate();
            if (updated <= 0) {
                log.warn("JobStart update fail jobId:{}", entity.getJobId());
            }
            Cmd.send(new ExecutionRunningCmd(entity.getExecutionId()));
            return updated > 0;
        });
    }

    /**
     * 更新上报时间等信息
     */
    private boolean report(JobEntity entity, TaskMonitor monitor, LocalDateTime reportAt) {
        return transactionService.transactional(() -> {
            int updated = entityManager.createQuery("update JobEntity " +
                    "set lastReportAt = :lastReportAt, monitor = :monitor, workerAddress = :workerAddress " +
                    "where jobId = :jobId and status = :status"
                )
                .setParameter("lastReportAt", reportAt)
                .setParameter("jobId", entity.getJobId())
                .setParameter("status", entity.getStatus())
                .setParameter("monitor", JacksonUtils.toJSONString(monitor))
                .setParameter("workerAddress", entity.getWorkerAddress())
                .executeUpdate();
            Cmd.send(new ExecutionRunningCmd(entity.getExecutionId()));
            return updated > 0;
        });
    }

    private boolean start(JobEntity entity, String workerAddress, LocalDateTime reportAt) {
        return transactionService.transactional(() -> {
            int updated = entityManager.createQuery("update JobEntity " +
                    "set status = :newStatus, startAt = :lastReportAt, lastReportAt = :lastReportAt, workerAddress = :workerAddress " +
                    "where jobId = :jobId and status in :oldStatuses"
                )
                .setParameter("lastReportAt", reportAt)
                .setParameter("jobId", entity.getJobId())
                .setParameter("newStatus", JobStatus.RUNNING.value)
                .setParameter("workerAddress", workerAddress)
                .setParameter("oldStatuses", Lists.newArrayList(
                    JobStatus.CREATED.value,
                    JobStatus.DISPATCHED.value
                ))
                .executeUpdate();
            if (updated <= 0) {
                log.warn("JobStart update fail jobId:{}", entity.getJobId());
            }
            Cmd.send(new ExecutionRunningCmd(entity.getExecutionId()));
            return updated > 0;
        });
    }


    @Transactional
    @CommandHandler
    public boolean handle(JobSuccessCmd cmd) {
        JobEntity entity = jobEntityRepo.findById(cmd.getJobId()).orElse(null);
        if (entity == null) {
            log.warn("JobSuccessCmd not found jobId:{}", cmd.getJobId());
            return false;
        }
        int updated = entityManager.createQuery("update JobEntity " +
                "set lastReportAt = :lastReportAt, status = :newStatus, startAt = :startAt, endAt = :endAt, monitor = :monitor " +
                "where jobId = :jobId and status = :oldStatus "
            )
            .setParameter("lastReportAt", cmd.getReportAt())
            .setParameter("startAt", entity.getStartAt() == null ? cmd.getReportAt() : entity.getStartAt())
            .setParameter("endAt", cmd.getReportAt())
            .setParameter("jobId", cmd.getJobId())
            .setParameter("oldStatus", JobStatus.RUNNING.value)
            .setParameter("newStatus", JobStatus.SUCCEED.value)
            .setParameter("monitor", cmd.getMonitor() == null ? "" : JacksonUtils.toJSONString(cmd.getMonitor()))
            .executeUpdate();
        if (updated <= 0) {
            log.warn("JobSuccessCmd update fail jobId:{}", cmd.getJobId());
            return false;
        }
        return true;
    }

    @Transactional
    @CommandHandler
    public boolean handle(JobFailCmd cmd) {
        JobEntity entity = jobEntityRepo.findById(cmd.getJobId()).orElse(null);
        if (entity == null) {
            log.warn("JobFailCmd not found jobId:{}", cmd.getJobId());
            return false;
        }
        int updated = entityManager.createQuery("update JobEntity " +
                "set lastReportAt = :lastReportAt, status = :newStatus, startAt = :startAt, endAt = :endAt," +
                " errorMsg = :errorMsg, monitor =:monitor " +
                "where jobId = :jobId and status in :oldStatus "
            )
            .setParameter("lastReportAt", cmd.getReportAt())
            .setParameter("startAt", entity.getStartAt() == null ? cmd.getReportAt() : entity.getStartAt())
            .setParameter("endAt", cmd.getReportAt())
            .setParameter("jobId", cmd.getJobId())
            .setParameter("oldStatus", Lists.newArrayList(
                JobStatus.RUNNING.value, // 运行时失败
                JobStatus.CREATED.value // broker下发失败
            ))
            .setParameter("newStatus", JobStatus.FAILED.value)
            .setParameter("errorMsg", cmd.getErrorMsg())
            .setParameter("monitor", cmd.getMonitor() == null ? "" : JacksonUtils.toJSONString(cmd.getMonitor()))
            .executeUpdate();
        if (updated <= 0) {
            log.warn("JobFailCmd update fail jobId:{}", cmd.getJobId());
            return false;
        }
        return true;
    }

}
