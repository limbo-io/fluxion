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

import io.fluxion.server.core.execution.cmd.ExecutionRetryScheduleCmd;
import io.fluxion.server.core.execution.fault.ExecutionInfo;
import io.fluxion.server.core.execution.fault.store.ExecutionStateStore;
import io.fluxion.server.core.job.cmd.JobRetryCmd;
import io.fluxion.server.infrastructure.schedule.task.DelayedTask;
import io.limbo.cqrs.spring.command.Cmd;
import io.limbo.cqrs.spring.annotation.CommandHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 执行重试服务
 */
@Slf4j
@Service
public class ExecutionRetryService {

    @Resource
    private ExecutionStateStore executionStateStore;

    @Resource
    private io.fluxion.server.infrastructure.schedule.scheduler.DelayedTaskScheduler delayedTaskScheduler;

    /**
     * 调度重试任务
     */
    public void scheduleRetry(String executionId, Duration delay) {
        log.info("[FAULT-RETRY] Scheduling retry: executionId={}, delay={}ms", executionId, delay.toMillis());

        LocalDateTime triggerAt = LocalDateTime.now().plusNanos(delay.toNanos());

        DelayedTask task = new DelayedTask(
            "fault-retry-" + executionId,
            triggerAt,
            t -> {
                ExecutionInfo info = executionStateStore.get(executionId);
                if (info == null || info.getState().isTerminal()) {
                    log.debug("[FAULT-RETRY] Skipping retry for terminal execution: executionId={}", executionId);
                    return;
                }

                if (info.getJobId() == null) {
                    log.warn("[FAULT-RETRY] Skipping retry without jobId: executionId={}", executionId);
                    return;
                }
                sendJobRetry(info.getJobId(), info.getRetryCount());
            }
        );

        delayedTaskScheduler.schedule(task);
    }

    @CommandHandler
    public void handle(ExecutionRetryScheduleCmd cmd) {
        scheduleRetry(cmd.getExecutionId(), cmd.getDelay());
    }

    protected void sendJobRetry(String jobId, int retryTimes) {
        Cmd.send(new JobRetryCmd(jobId, retryTimes));
    }
}
