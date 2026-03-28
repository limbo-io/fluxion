/*
 * Copyright 2025-2030 Fluxion Team (https://github.com/Fluxion-io).
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

import io.fluxion.remote.core.constants.JobStatus;
import io.fluxion.server.core.execution.cmd.ExecutionMigrateCmd;
import io.fluxion.server.core.execution.fault.ExecutionInfo;
import io.fluxion.server.core.execution.fault.ExecutionState;
import io.fluxion.server.core.execution.fault.store.ExecutionStateStore;
import io.fluxion.server.core.job.Job;
import io.fluxion.server.core.job.JobType;
import io.fluxion.server.core.job.cmd.JobResetCmd;
import io.fluxion.server.core.job.cmd.JobRunCmd;
import io.fluxion.server.infrastructure.dao.entity.JobEntity;
import io.fluxion.server.infrastructure.dao.repository.JobEntityRepo;
import io.limbo.cqrs.spring.command.Cmd;
import io.limbo.cqrs.spring.annotation.CommandHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 执行迁移服务
 * 用于故障迁移场景，将执行任务从故障 Worker 迁移到其他可用 Worker
 */
@Slf4j
@Service
public class ExecutionMigrateService {

    @Resource
    private ExecutionStateStore executionStateStore;

    @Resource
    private JobEntityRepo jobEntityRepo;

    /**
     * 执行迁移
     * @param executionId 执行ID
     * @param sourceWorkerId 源Worker ID (故障Worker)
     */
    public void migrate(String executionId, String sourceWorkerId) {
        log.info("[FAULT-MIGRATE] Starting migration: executionId={}, sourceWorkerId={}",
            executionId, sourceWorkerId);

        ExecutionInfo info = executionStateStore.get(executionId);
        if (info == null) {
            log.warn("[FAULT-MIGRATE] Execution not found in state store: executionId={}", executionId);
            return;
        }

        // 检查状态是否为 MIGRATING
        if (info.getState() != ExecutionState.MIGRATING) {
            log.warn("[FAULT-MIGRATE] Execution is not in MIGRATING state: executionId={}, state={}",
                executionId, info.getState());
            return;
        }

        // 根据 jobId 查找 Job 实体
        String jobId = info.getJobId();
        JobEntity jobEntity = jobEntityRepo.findById(jobId).orElse(null);
        if (jobEntity == null) {
            log.error("[FAULT-MIGRATE] Job not found: jobId={}, executionId={}", jobId, executionId);
            // 更新状态为失败
            info.setState(ExecutionState.FAILED);
            return;
        }

        // 重置 Job 状态为 INITED，以便重新分发
        Cmd.send(new JobResetCmd(jobId));

        // 更新 ExecutionInfo 状态并清除旧 Worker 信息
        info.setState(ExecutionState.RUNNING);
        info.setWorkerId(null);
        info.setLastError(null);

        // 重新创建 Job 并触发运行
        Job job = new Job();
        job.setJobId(jobId);
        job.setExecutionId(executionId);
        job.setRefId(info.getTaskId());
        job.setType(JobType.valueOf(info.getJobType()));
        job.setStatus(JobStatus.INITED);
        job.setRetryTimes(info.getRetryCount());

        // 触发 Job 重新运行
        Cmd.send(new JobRunCmd(job));

        log.info("[FAULT-MIGRATE] Migration completed: executionId={}, jobId={}", executionId, jobId);
    }

    @CommandHandler
    public void handle(ExecutionMigrateCmd cmd) {
        migrate(cmd.getExecutionId(), cmd.getSourceWorkerId());
    }
}