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
import io.fluxion.server.core.executor.option.RetryOption;
import lombok.Data;

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

    private String result;

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

}
