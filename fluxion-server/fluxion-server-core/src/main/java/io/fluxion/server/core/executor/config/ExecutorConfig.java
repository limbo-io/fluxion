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

package io.fluxion.server.core.executor.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import io.fluxion.common.utils.json.JacksonTypeIdResolver;
import io.fluxion.remote.core.constants.ExecuteType;
import io.fluxion.server.core.executor.option.DispatchOption;
import io.fluxion.server.core.executor.option.OvertimeOption;
import io.fluxion.server.core.executor.option.RetryOption;
import io.fluxion.server.infrastructure.validata.ValidatableConfig;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务执行器
 *
 * @author Devil
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.CLASS,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true
)
@JsonTypeIdResolver(JacksonTypeIdResolver.class)
@Data
@NoArgsConstructor
public abstract class ExecutorConfig implements ValidatableConfig {

    /**
     * 类型
     *
     * @see Type
     */
    private String type;

    /**
     * 所属应用
     */
    private String appId;

    /**
     * 执行方式
     */
    private ExecuteType executeType;

    /**
     * 下发属性
     */
    private DispatchOption dispatchOption;

    public abstract String executorName();

    public interface Type {
        String CUSTOM = "custom";
        String PYTHON_SCRIPT = "python_script";
        String SHELL_SCRIPT = "shell_script";
    }
}