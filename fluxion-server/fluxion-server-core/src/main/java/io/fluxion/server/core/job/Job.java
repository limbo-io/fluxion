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

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import io.fluxion.common.utils.json.JacksonTypeIdResolver;
import io.fluxion.remote.core.constants.JobStatus;
import io.fluxion.server.core.execution.Executable;
import io.fluxion.server.core.execution.Execution;
import io.fluxion.server.core.execution.query.ExecutionByIdQuery;
import io.fluxion.server.core.executor.option.RetryOption;
import io.fluxion.server.core.job.cmd.JobFailCmd;
import io.fluxion.server.core.job.cmd.JobRetryCmd;
import io.fluxion.server.core.job.cmd.JobSuccessCmd;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Query;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * @author Devil
 */
@Data
public class Job {

    private String jobId;

    /**
     * 关联的 id
     * workflow -> nodeId
     * executor -> 空
     */
    private String refId;

    private JobStatus status = JobStatus.INITED;

    private LocalDateTime triggerAt;

    private String executionId;

    /**
     * 当前是第几次重试
     */
    private int retryTimes = 0;

    private JobMonitor jobMonitor;

    private JobType type;

    // when success
    private String result;

    // when error
    private String errorMsg;

    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private Execution execution;

    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private Job.Config config;

    public boolean success(LocalDateTime time) {
        boolean success = Cmd.send(new JobSuccessCmd(jobId, time, jobMonitor));
        if (!success) {
            return false;
        }
        return execution().executable().success(this, time);
    }

    public boolean fail(LocalDateTime time) {
        if (config().retryOption.canRetry(retryTimes)) {
            return Cmd.send(new JobRetryCmd());
        }
        // 更新当前job状态
        boolean failed = Cmd.send(new JobFailCmd(jobId, time, errorMsg, jobMonitor));
        if (!failed) {
            return false;
        }
        return execution().executable().fail(this, time);
    }

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.CLASS,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true
    )
    @JsonTypeIdResolver(JacksonTypeIdResolver.class)
    @Data
    public static abstract class Config {

        private String type;

        /**
         * 重试参数
         */
        private RetryOption retryOption = new RetryOption();
    }

    public Execution execution() {
        if (execution == null) {
            execution = Query.query(new ExecutionByIdQuery(executionId)).getExecution();
        }
        return execution;
    }

    public Job.Config config() {
        if (config == null) {
            Executable executable = execution().executable();
            config = executable.config(refId);
        }
        return config;
    }

}
