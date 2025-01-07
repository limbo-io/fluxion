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

package io.fluxion.server.converter;

import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.platform.dao.entity.TriggerEntity;
import io.fluxion.platform.trigger.TriggerConfig;
import io.fluxion.server.api.trigger.view.TriggerView;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
public class TriggerConverter {

    public static List<TriggerView> toView(List<TriggerEntity> entities) {
        if (CollectionUtils.isEmpty(entities)) {
            return Collections.emptyList();
        }
        return entities.stream().map(TriggerConverter::toView).collect(Collectors.toList());
    }

    public static TriggerView toView(TriggerEntity entity) {
        TriggerView triggerView = new TriggerView();
        triggerView.setId(entity.getTriggerId());
        triggerView.setType(entity.getType());
        triggerView.setRefId(entity.getRefId());
        triggerView.setRefType(entity.getRefType());
        triggerView.setDescription(entity.getDescription());
        triggerView.setConfig(JacksonUtils.toType(entity.getConfig(), TriggerConfig.class));
        triggerView.setEnabled(entity.isEnabled());
        return triggerView;
    }

}
