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

package io.fluxion.server.core.execution.config;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.fluxion.server.core.execution.Executable;
import io.fluxion.server.core.execution.ExecuteConfig;
import io.fluxion.server.core.execution.ExecuteType;
import io.fluxion.server.core.executor.config.ExecutorConfig;
import io.fluxion.server.core.executor.option.OvertimeOption;
import io.fluxion.server.core.executor.option.RetryOption;
import io.fluxion.server.core.trigger.Trigger;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 基于调度配置执行后续
 *
 * @author Devil
 */
@EqualsAndHashCode(callSuper = true)
@Data
@JsonTypeName(ExecuteType.Val.EXECUTOR)
public class ExecutorExecuteConfig extends ExecuteConfig {

    private ExecutorConfig executor;

    /**
     * 重试参数
     */
    private RetryOption retryOption;

    /**
     * 超时参数
     */
    private OvertimeOption overtimeOption;

    @Override
    public String executeId() {
        return executor.executorName();
    }
}
