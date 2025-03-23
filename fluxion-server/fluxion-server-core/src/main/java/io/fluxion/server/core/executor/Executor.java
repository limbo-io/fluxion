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

package io.fluxion.server.core.executor;

import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.server.core.context.RunContext;
import io.fluxion.server.core.execution.Executable;
import io.fluxion.server.core.execution.cmd.ExecutionSuccessCmd;
import io.fluxion.server.core.executor.config.ExecutorConfig;
import io.fluxion.server.core.executor.option.OvertimeOption;
import io.fluxion.server.core.executor.option.RetryOption;
import io.fluxion.server.core.task.ExecutorTask;
import io.fluxion.server.core.task.Task;
import io.fluxion.server.core.task.cmd.TaskSuccessCmd;
import io.fluxion.server.core.task.cmd.TasksCreateCmd;
import io.fluxion.server.infrastructure.cqrs.Cmd;

import java.time.LocalDateTime;
import java.util.Collections;

/**
 * @author Devil
 */
public class Executor implements Executable {

    private ExecutorConfig config;

    /**
     * 重试参数
     */
    private RetryOption retryOption = new RetryOption();

    /**
     * 超时参数
     */
    private OvertimeOption overtimeOption = new OvertimeOption();

    private Executor() {
    }

    private Executor(ExecutorConfig config, RetryOption retryOption, OvertimeOption overtimeOption) {
        this.config = config;
        this.retryOption = retryOption == null ? new RetryOption() : retryOption;
        this.overtimeOption = overtimeOption == null ? new OvertimeOption() : overtimeOption;
    }

    public static Executor of(ExecutorConfig config, RetryOption retryOption, OvertimeOption overtimeOption) {
        return new Executor(config, retryOption, overtimeOption);
    }

    @Override
    public void execute(RunContext context) {
        Task task = task(context.executionId(), TimeUtils.currentLocalDateTime());
        // 保存数据
        Cmd.send(new TasksCreateCmd(Collections.singletonList(task)));
        // 执行
        task.schedule();
    }

    @Override
    public boolean success(String refId, String taskId, String executionId, String workerAddress, LocalDateTime time) {
        Boolean success = Cmd.send(new TaskSuccessCmd(taskId, workerAddress, time));
        if (!success) {
            return false;
        }
        return Cmd.send(new ExecutionSuccessCmd(executionId, time));
    }

    @Override
    public RetryOption retryOption(String refId) {
        return retryOption;
    }

    private Task task(String executionId, LocalDateTime triggerAt) {
        ExecutorTask task = new ExecutorTask();
        task.setAppId(config.getAppId());
        task.setExecutorName(config.executorName());
        task.setDispatchOption(config.getDispatchOption());
        task.setExecuteMode(config.getExecuteMode());
        task.setExecutionId(executionId);
        task.setTriggerAt(triggerAt);
        task.setRefId("");
        return task;
    }
}
