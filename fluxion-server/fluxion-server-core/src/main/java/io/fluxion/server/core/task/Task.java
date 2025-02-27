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

package io.fluxion.server.core.task;

import io.fluxion.common.thread.CommonThreadPool;
import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.executor.option.OvertimeOption;
import io.fluxion.server.core.executor.option.RetryOption;
import io.fluxion.server.core.task.cmd.TaskRunCmd;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.schedule.schedule.DelayedTaskScheduler;
import io.fluxion.server.infrastructure.schedule.task.DelayedTaskFactory;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author Devil
 */
@Data
public abstract class Task {

    private String executionId;

    private String taskId;

    /**
     * 关联的 id
     * flow 中是nodeId
     * executor 则直接为其id
     */
    private String refId;

    private LocalDateTime triggerAt;

    /**
     * 当前是第几次重试
     */
    private int retryTimes;

    /**
     * 重试参数
     */
    private RetryOption retryOption;

    /**
     * 超时参数
     */
    private OvertimeOption overtimeOption;

    /**
     * 执行失败是否继续
     * true  会继续执行后续作业
     * false 终止环节
     */
    private boolean skipWhenFail = false;

    public void schedule() {
        DelayedTaskScheduler delayedTaskScheduler = BrokerContext.broker().delayedTaskScheduler();
        delayedTaskScheduler.schedule(DelayedTaskFactory.create(
            scheduleId(),
            triggerAt,
            delayedTask -> CommonThreadPool.IO.submit(() -> Cmd.send(new TaskRunCmd(this)))
        ));
    }

    private String scheduleId() {
        return "t_" + taskId;
    }

    public abstract TaskType type();

    public boolean canRetry() {
        return retryTimes < retryOption.getRetryTimes();
    }
}
