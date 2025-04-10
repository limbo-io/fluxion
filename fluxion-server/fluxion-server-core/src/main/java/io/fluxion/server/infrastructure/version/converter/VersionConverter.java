/*
 * Copyright 2025-2030 limbo-io Team (https://github.com/limbo-io).
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

package io.fluxion.server.infrastructure.version.converter;

import io.fluxion.server.infrastructure.dao.entity.VersionEntity;
import io.fluxion.server.infrastructure.version.model.Version;
import io.fluxion.server.infrastructure.version.model.VersionRefType;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
public class VersionConverter {

    public static List<Version> convertEntities(List<VersionEntity> entities) {
        if (CollectionUtils.isEmpty(entities)) {
            return Collections.emptyList();
        }
        return entities.stream().map(VersionConverter::convert).collect(Collectors.toList());
    }

    public static Version convert(VersionEntity entity) {
        if (entity == null) {
            return null;
        }
        Version version = new Version();
        version.setId(convert(entity.getId()));
        version.setDescription(entity.getDescription());
        version.setConfig(entity.getConfig());
        return version;
    }

    public static VersionEntity.ID convert(Version.ID id) {
        return new VersionEntity.ID(id.getRefId(), id.getRefType().value, id.getVersion());
    }

    public static Version.ID convert(VersionEntity.ID id) {
        return new Version.ID(id.getRefId(), VersionRefType.parse(id.getRefType()), id.getVersion());
    }

}
