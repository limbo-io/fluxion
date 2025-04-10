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

package io.fluxion.server.core.execution;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import io.fluxion.common.utils.json.JacksonTypeIdResolver;
import io.fluxion.server.core.executor.option.OvertimeOption;
import io.fluxion.server.core.executor.option.RetryOption;
import io.fluxion.server.infrastructure.validata.ValidatableConfig;
import lombok.Data;

/**
 * 执行配置
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
public abstract class ExecuteConfig implements ValidatableConfig {
    /**
     * 触发方式
     *
     * @see ExecutableType
     */
    private String type;

    /**
     * 重试参数
     */
    private RetryOption retryOption;

    /**
     * 超时参数
     */
    private OvertimeOption overtimeOption;

    public ExecutableType type() {
        return ExecutableType.parse(type);
    }

}