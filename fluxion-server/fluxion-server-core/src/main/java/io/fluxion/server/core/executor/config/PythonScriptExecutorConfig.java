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

package io.fluxion.server.core.executor.config;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.fluxion.server.core.workflow.WorkflowConstants;
import io.fluxion.server.infrastructure.validata.ValidateSuppressInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Devil
 */
@EqualsAndHashCode(callSuper = true)
@Data
@JsonTypeName(ExecutorConfig.Type.PYTHON_SCRIPT)
public class PythonScriptExecutorConfig extends ExecutorConfig {
    /**
     * 脚本
     */
    private String script;

    @Override
    public List<ValidateSuppressInfo> validate() {
        List<ValidateSuppressInfo> infos = new ArrayList<>();
        if (StringUtils.isBlank(script)) {
            infos.add(new ValidateSuppressInfo(WorkflowConstants.SCRIPT_IS_EMPTY));
        }
        return infos;
    }

    @Override
    public String executorName() {
        return "fluxion.PythonScriptExecutor";
    }
}