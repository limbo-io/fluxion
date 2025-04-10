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

package io.fluxion.server.start.api.execution.view;

import io.fluxion.server.core.execution.ExecutableType;
import io.fluxion.server.core.execution.ExecutionStatus;
import io.fluxion.server.core.trigger.TriggerType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author Devil
 */
@Data
public class ExecutionView {

    private String executionId;

    private String triggerId;

    /**
     * @see TriggerType
     */
    private String triggerType;

    /**
     * @see ExecutableType
     */
    private String executableType;

    /**
     * workflow -> workflowId
     * executor -> triggerId
     */
    private String executableId;
    /**
     * 版本
     */
    private String executableVersion;

    /**
     * 状态
     *
     * @see ExecutionStatus
     */
    private String status;

    /**
     * 期望的调度触发时间
     */
    private LocalDateTime triggerAt;

    /**
     * 执行开始时间
     */
    private LocalDateTime startAt;

    /**
     * 执行结束时间
     */
    private LocalDateTime endAt;

}
