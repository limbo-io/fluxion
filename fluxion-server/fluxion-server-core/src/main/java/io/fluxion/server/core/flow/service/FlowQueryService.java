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

package io.fluxion.server.core.flow.service;

import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.server.core.flow.Flow;
import io.fluxion.server.core.flow.FlowConfig;
import io.fluxion.server.core.flow.converter.FlowEntityConverter;
import io.fluxion.server.core.flow.query.FlowByIdQuery;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.dao.entity.FlowEntity;
import io.fluxion.server.infrastructure.dao.repository.FlowEntityRepo;
import io.fluxion.server.infrastructure.version.model.Version;
import io.fluxion.server.infrastructure.version.model.VersionMode;
import io.fluxion.server.infrastructure.version.query.VersionByIdQuery;
import org.apache.commons.lang3.StringUtils;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @author Devil
 */
@Service
public class FlowQueryService {

    @Resource
    private FlowEntityRepo flowEntityRepo;

    @QueryHandler
    public FlowByIdQuery.Response handle(FlowByIdQuery query) {
        String flowId = query.getId();
        FlowEntity entity = flowEntityRepo.findByFlowIdAndDeleted(flowId, false).orElse(null);
        if (entity == null) {
            return new FlowByIdQuery.Response(null);
        }
        String vs = null;
        if (StringUtils.isNotBlank(query.getVersion())) {
            vs = query.getVersion();
        } else {
            if (VersionMode.PUBLISH == query.getVersionMode()) {
                vs = entity.getPublishVersion();
            } else if (VersionMode.DRAFT == query.getVersionMode()) {
                vs = entity.getDraftVersion();
            } else if (VersionMode.PUBLISH_FIRST == query.getVersionMode()) {
                vs = StringUtils.isBlank(entity.getPublishVersion()) ? entity.getDraftVersion() : entity.getPublishVersion();
            }
        }
        FlowConfig flowConfig = null;
        if (StringUtils.isNotBlank(vs)) {
            Version version = Query.query(
                new VersionByIdQuery(FlowEntityConverter.versionId(entity.getFlowId(), vs))
            ).getVersion();
            flowConfig = JacksonUtils.toType(version.getConfig(), FlowConfig.class);
        }
        return new FlowByIdQuery.Response(Flow.of(entity.getFlowId(), flowConfig));
    }


}
