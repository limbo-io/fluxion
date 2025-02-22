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

package io.fluxion.server.infrastructure.version.handler;

import io.fluxion.server.infrastructure.dao.entity.VersionEntity;
import io.fluxion.server.infrastructure.dao.repository.VersionEntityRepo;
import io.fluxion.server.infrastructure.version.model.Version;
import io.fluxion.server.infrastructure.version.model.VersionRefType;
import io.fluxion.server.infrastructure.version.query.VersionByIdQuery;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author Devil
 */
@Slf4j
@Component
public class VersionQueryService {

    @Resource
    private VersionEntityRepo versionEntityRepo;

    @QueryHandler
    public VersionByIdQuery.Response handle(VersionByIdQuery query) {
        VersionEntity entity = versionEntityRepo.findById(new VersionEntity.ID(query.getRefId(), query.getRefType().value, query.getVersion())).orElse(null);
        return new VersionByIdQuery.Response(to(entity));
    }

    private Version to(VersionEntity entity) {
        if (entity == null) {
            return null;
        }
        Version version = new Version();
        version.setRefId(entity.getId().getRefId());
        version.setRefType(VersionRefType.parse(entity.getId().getRefType()));
        version.setVersion(entity.getId().getVersion());
        version.setConfig(entity.getConfig());
        return version;
    }

}
