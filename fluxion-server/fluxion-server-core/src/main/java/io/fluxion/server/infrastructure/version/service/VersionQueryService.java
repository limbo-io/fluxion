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

package io.fluxion.server.infrastructure.version.service;

import io.fluxion.server.infrastructure.dao.entity.VersionEntity;
import io.fluxion.server.infrastructure.dao.repository.VersionEntityRepo;
import io.fluxion.server.infrastructure.version.converter.VersionConverter;
import io.fluxion.server.infrastructure.version.model.Version;
import io.fluxion.server.infrastructure.version.model.VersionRefType;
import io.fluxion.server.infrastructure.version.query.VersionByIdQuery;
import io.fluxion.server.infrastructure.version.query.VersionByIdsQuery;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

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
        Version.ID versionId = query.getId();
        if (StringUtils.isBlank(versionId.getRefId()) || StringUtils.isBlank(versionId.getVersion())
            || versionId.getRefType() == null || versionId.getRefType() == VersionRefType.UNKNOWN) {
            return new VersionByIdQuery.Response(null);
        }
        VersionEntity entity = versionEntityRepo.findById(VersionConverter.convert(versionId)).orElse(null);
        return new VersionByIdQuery.Response(VersionConverter.convert(entity));
    }

    @QueryHandler
    public VersionByIdsQuery.Response handle(VersionByIdsQuery query) {
        if (CollectionUtils.isEmpty(query.getIds())) {
            return new VersionByIdsQuery.Response();
        }
        List<VersionEntity.ID> ids = query.getIds().stream().map(VersionConverter::convert).collect(Collectors.toList());
        List<VersionEntity> entities = versionEntityRepo.findAllById(ids);
        return new VersionByIdsQuery.Response(VersionConverter.convertEntities(entities));
    }

}
