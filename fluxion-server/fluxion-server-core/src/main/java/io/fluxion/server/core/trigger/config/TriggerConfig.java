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

package io.fluxion.server.core.trigger.config;

import io.fluxion.server.core.flow.FlowConstants;
import io.fluxion.server.infrastructure.validata.ValidatableConfig;
import io.fluxion.server.infrastructure.validata.ValidateSuppressInfo;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Trigger 配置态
 *
 * @author Devil
 */
@Data
public class TriggerConfig implements ValidatableConfig {

    private Trigger trigger;

    @Override
    public List<ValidateSuppressInfo> validate() {
        if (trigger == null) {
            return Collections.singletonList(new ValidateSuppressInfo(FlowConstants.TRIGGER_IS_EMPTY));
        }
        return new ArrayList<>(trigger.validate());
    }

}
