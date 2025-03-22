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

package io.fluxion.server.core.trigger.service;

import io.fluxion.server.core.trigger.Trigger;
import io.fluxion.server.core.trigger.converter.TriggerEntityConverter;
import io.fluxion.server.core.trigger.query.TriggerByIdQuery;
import io.fluxion.server.core.trigger.query.TriggerByIdsQuery;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.dao.entity.TriggerEntity;
import io.fluxion.server.infrastructure.dao.repository.TriggerEntityRepo;
import io.fluxion.server.infrastructure.version.model.Version;
import io.fluxion.server.infrastructure.version.model.VersionMode;
import io.fluxion.server.infrastructure.version.query.VersionByIdQuery;
import io.fluxion.server.infrastructure.version.query.VersionByIdsQuery;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
@Service
public class TriggerQueryService {

    @Resource
    private TriggerEntityRepo triggerEntityRepo;

    @QueryHandler
    public TriggerByIdQuery.Response handle(TriggerByIdQuery query) {
        TriggerEntity entity = triggerEntityRepo.findById(query.getId()).orElse(null);
        if (entity == null) {
            return new TriggerByIdQuery.Response(null);
        }
        String vs;
        if (StringUtils.isNotBlank(query.getVersion())) {
            vs = query.getVersion();
        } else {
            vs = getVersion(entity, query.getVersionMode());
        }
        Version version = null;
        if (StringUtils.isNotBlank(vs)) {
            version = Query.query(new VersionByIdQuery(
                TriggerEntityConverter.versionId(entity.getTriggerId(), vs)
            )).getVersion();
        }
        Trigger trigger = TriggerEntityConverter.convert(entity, version);
        return new TriggerByIdQuery.Response(trigger);
    }

    @QueryHandler
    public TriggerByIdsQuery.Response handle(TriggerByIdsQuery query) {
        List<TriggerEntity> entities = triggerEntityRepo.findByTriggerIdInAndDeleted(query.getIds(), false);
        if (CollectionUtils.isEmpty(entities)) {
            return new TriggerByIdsQuery.Response();
        }
        List<Version.ID> versionIds = entities.stream().map(e -> {
            String version = getVersion(e, query.getVersionMode());
            return TriggerEntityConverter.versionId(e.getTriggerId(), version);
        }).collect(Collectors.toList());
        List<Version> versions = Query.query(new VersionByIdsQuery(versionIds)).getVersions();
        List<Trigger> triggers = TriggerEntityConverter.convert(entities, versions);
        return new TriggerByIdsQuery.Response(triggers);

    }

    private String getVersion(TriggerEntity entity, VersionMode mode) {
        if (VersionMode.PUBLISH == mode) {
            return entity.getPublishVersion();
        } else if (VersionMode.DRAFT == mode) {
            return entity.getDraftVersion();
        } else if (VersionMode.PUBLISH_FIRST == mode) {
            return StringUtils.isBlank(entity.getPublishVersion()) ? entity.getDraftVersion() : entity.getPublishVersion();
        }
        return null;
    }

}
