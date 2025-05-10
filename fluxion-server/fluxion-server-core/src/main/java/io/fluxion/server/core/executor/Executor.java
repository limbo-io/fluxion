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

import io.fluxion.common.thread.CommonThreadPool;
import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.server.core.execution.Executable;
import io.fluxion.server.core.execution.ExecutableType;
import io.fluxion.server.core.execution.Execution;
import io.fluxion.server.core.execution.cmd.ExecutionFailCmd;
import io.fluxion.server.core.execution.cmd.ExecutionSuccessCmd;
import io.fluxion.server.core.executor.config.ExecutorConfig;
import io.fluxion.server.core.executor.option.OvertimeOption;
import io.fluxion.server.core.executor.option.RetryOption;
import io.fluxion.server.core.job.Job;
import io.fluxion.server.core.job.JobType;
import io.fluxion.server.core.job.cmd.JobRunCmd;
import io.fluxion.server.core.job.cmd.JobsCreateCmd;
import io.fluxion.server.core.job.config.ExecutorJobConfig;
import io.fluxion.server.infrastructure.concurrent.LoggingTask;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Collections;

/**
 * @author Devil
 */
public class Executor implements Executable {

    private String id;

    private String version;

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

    private Executor(String id, String version, ExecutorConfig config, RetryOption retryOption, OvertimeOption overtimeOption) {
        this.id = id;
        this.version = version;
        this.config = config;
        this.retryOption = retryOption == null ? new RetryOption() : retryOption;
        this.overtimeOption = overtimeOption == null ? new OvertimeOption() : overtimeOption;
    }

    public static Executor of(String id, String version, ExecutorConfig config, RetryOption retryOption, OvertimeOption overtimeOption) {
        return new Executor(id, version, config, retryOption, overtimeOption);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public ExecutableType type() {
        return ExecutableType.EXECUTOR;
    }

    @Override
    public void execute(Execution execution) {
        Job job = new Job();
        job.setType(JobType.EXECUTOR);
        job.setTriggerAt(TimeUtils.currentLocalDateTime());
        // 保存数据
        Cmd.send(new JobsCreateCmd(Collections.singletonList(job)));
        // 执行
        CommonThreadPool.IO.submit(new LoggingTask(() -> Cmd.send(new JobRunCmd(job))));
    }

    @Override
    public boolean success(String executionId, String refId, LocalDateTime time) {
        return Cmd.send(new ExecutionSuccessCmd(executionId, time));
    }

    @Override
    public boolean fail(String executionId, String refId, LocalDateTime time) {
        return Cmd.send(new ExecutionFailCmd(executionId, time));
    }

    @Override
    public Job.Config config(String refId) {
        ExecutorJobConfig jobConfig = new ExecutorJobConfig();
        jobConfig.setAppId(config.getAppId());
        jobConfig.setExecutorName(config.executorName());
        jobConfig.setDispatchOption(config.getDispatchOption());
        jobConfig.setExecuteMode(config.getExecuteMode());
        jobConfig.setRetryOption(retryOption);
        return jobConfig;
    }

}
