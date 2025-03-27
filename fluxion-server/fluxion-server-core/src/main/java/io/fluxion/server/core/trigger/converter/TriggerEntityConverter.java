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
import io.fluxion.server.core.trigger.Trigger;
import io.fluxion.server.core.trigger.TriggerConfig;
import io.fluxion.server.infrastructure.dao.entity.TriggerEntity;
import io.fluxion.server.infrastructure.version.model.Version;
import io.fluxion.server.infrastructure.version.model.VersionRefType;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
public class TriggerEntityConverter {

    public static List<Trigger> convert(List<TriggerEntity> entities, List<Version> versions) {
        if (CollectionUtils.isEmpty(entities)) {
            return Collections.emptyList();
        }
        if (versions == null) {
            versions = Collections.emptyList();
        }
        Map<String, Version> versionMap = versions.stream().collect(Collectors.toMap(version -> version.getId().getRefId(), version -> version));
        return entities.stream().map(e -> {
            Version version = versionMap.get(e.getTriggerId());
            return convert(e, version);
        }).collect(Collectors.toList());
    }

    public static Trigger convert(TriggerEntity entity, Version version) {
        Trigger trigger = new Trigger();
        trigger.setId(entity.getTriggerId());
        trigger.setName(entity.getName());
        trigger.setDescription(entity.getDescription());
        trigger.setEnabled(entity.isEnabled());
        if (version != null) {
            trigger.setPublished(StringUtils.isNotBlank(version.getId().getVersion()));
            trigger.setConfig(JacksonUtils.toType(version.getConfig(), TriggerConfig.class));
        }
        return trigger;
    }

    public static String config(TriggerConfig config) {
        return JacksonUtils.toJSONString(config);
    }

    public static Version.ID versionId(String triggerId) {
        return new Version.ID(triggerId, VersionRefType.TRIGGER, null);
    }

    public static Version.ID versionId(String triggerId, String version) {
        return new Version.ID(triggerId, VersionRefType.TRIGGER, version);
    }

}
