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

package io.fluxion.server.core.job;

import io.fluxion.common.thread.CommonThreadPool;
import io.fluxion.remote.core.constants.JobStatus;
import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.execution.Execution;
import io.fluxion.server.core.executor.option.RetryOption;
import io.fluxion.server.core.job.cmd.JobFailCmd;
import io.fluxion.server.core.job.cmd.JobRetryCmd;
import io.fluxion.server.core.job.cmd.JobRunCmd;
import io.fluxion.server.core.job.cmd.JobSuccessCmd;
import io.fluxion.server.infrastructure.concurrent.LoggingTask;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.schedule.schedule.DelayedTaskScheduler;
import io.fluxion.server.infrastructure.schedule.task.DelayedTaskFactory;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author Devil
 */
@Data
public abstract class Job {

    private String jobId;

    /**
     * 关联的 id
     * workflow -> nodeId
     * executor -> 空
     */
    private String refId;

    private JobStatus status = JobStatus.CREATED;

    private LocalDateTime triggerAt;

    private Execution execution;

    /**
     * 当前是第几次重试
     */
    private int retryTimes = 0;

    private TaskMonitor taskMonitor;

    /**
     * 重试参数
     */
    private RetryOption retryOption = new RetryOption();

    // when success
    private String result;

    // when error
    private String errorMsg;

    public void schedule() {
        DelayedTaskScheduler delayedTaskScheduler = BrokerContext.broker().delayedTaskScheduler();
        delayedTaskScheduler.schedule(DelayedTaskFactory.create(
            scheduleId(),
            triggerAt,
            delayedTask -> CommonThreadPool.IO.submit(new LoggingTask(() -> Cmd.send(new JobRunCmd(this))))
        ));
    }

    private String scheduleId() {
        return "t_" + jobId;
    }

    public abstract JobType type();

    public boolean success(LocalDateTime time) {
        boolean success = Cmd.send(new JobSuccessCmd(jobId, time, taskMonitor));
        if (!success) {
            return false;
        }
        return execution.getExecutable().success(this, time);
    }

    public boolean fail(LocalDateTime time) {
        if (retryOption.canRetry(retryTimes)) {
            return Cmd.send(new JobRetryCmd());
        }
        // 更新当前job状态
        boolean failed = Cmd.send(new JobFailCmd(jobId, time, errorMsg, taskMonitor));
        if (!failed) {
            return false;
        }
        return execution.getExecutable().fail(this, time);
    }

}
