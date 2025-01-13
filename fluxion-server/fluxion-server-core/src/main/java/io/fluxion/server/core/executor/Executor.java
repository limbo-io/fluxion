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

package io.fluxion.server.core.executor;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import io.fluxion.common.utils.json.JacksonTypeIdResolver;
import io.fluxion.server.core.executor.option.DispatchOption;
import io.fluxion.server.core.executor.option.OvertimeOption;
import io.fluxion.server.core.executor.option.RetryOption;
import io.fluxion.server.core.output.Output;
import io.fluxion.server.core.task.Task;
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
public abstract class Executor implements ValidatableConfig {

    /**
     * 类型
     *
     * @see Type
     */
    private String type;

    /**
     * 超时参数
     */
    private OvertimeOption overtimeOption;

    /**
     * 重试参数
     */
    private RetryOption retryOption;

    /**
     * 下发属性
     */
    private DispatchOption dispatchOption;

    /**
     * 运行业务逻辑
     *
     * @param task 执行的任务
     * @return 输出
     */
    public Output run(Task task) {
        return null;
    }

    public interface Type {
        String CUSTOM = "custom";
        String PYTHON_SCRIPT = "python_script";
        String SHELL_SCRIPT = "shell_script";
    }
}