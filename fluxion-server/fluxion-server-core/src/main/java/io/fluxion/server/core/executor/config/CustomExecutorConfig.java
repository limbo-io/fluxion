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
import io.fluxion.server.core.flow.FlowConstants;
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
@JsonTypeName(ExecutorConfig.Type.CUSTOM)
public class CustomExecutorConfig extends ExecutorConfig {
    /**
     * 执行器名称
     */
    private String name;



    @Override
    public List<ValidateSuppressInfo> validate() {
        List<ValidateSuppressInfo> infos = new ArrayList<>();
        if (StringUtils.isBlank(name)) {
            infos.add(new ValidateSuppressInfo(FlowConstants.EXECUTOR_NAME_IS_EMPTY));
        }
        return infos;
    }
}