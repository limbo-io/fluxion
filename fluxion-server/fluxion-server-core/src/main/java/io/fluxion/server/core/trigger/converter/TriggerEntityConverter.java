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

package io.fluxion.server.core.trigger.converter;

import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.server.core.execution.ExecuteConfig;
import io.fluxion.server.core.trigger.Trigger;
import io.fluxion.server.core.trigger.TriggerConfig;
import io.fluxion.server.infrastructure.dao.entity.TriggerEntity;

/**
 * @author Devil
 */
public class TriggerEntityConverter {

    public static Trigger convert(TriggerEntity entity) {
        Trigger trigger = new Trigger();
        trigger.setId(entity.getTriggerId());
        trigger.setName(entity.getName());
        trigger.setDescription(entity.getDescription());
        trigger.setEnabled(entity.isEnabled());
        trigger.setTriggerConfig(JacksonUtils.toType(entity.getTriggerConfig(), TriggerConfig.class));
        trigger.setExecuteConfig(JacksonUtils.toType(entity.getExecuteConfig(), ExecuteConfig.class));
        return trigger;
    }

}
