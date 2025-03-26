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

package io.fluxion.server.core.workflow.service;

import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.server.core.workflow.Workflow;
import io.fluxion.server.core.workflow.WorkflowConfig;
import io.fluxion.server.core.workflow.converter.WorkflowEntityConverter;
import io.fluxion.server.core.workflow.query.WorkflowByIdQuery;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.dao.entity.WorkflowEntity;
import io.fluxion.server.infrastructure.dao.repository.WorkflowEntityRepo;
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
public class WorkflowQueryService {

    @Resource
    private WorkflowEntityRepo workflowEntityRepo;

    @QueryHandler
    public WorkflowByIdQuery.Response handle(WorkflowByIdQuery query) {
        WorkflowEntity entity = workflowEntityRepo.findByWorkflowIdAndDeleted(query.getId(), false).orElse(null);
        if (entity == null) {
            return new WorkflowByIdQuery.Response(null);
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
        WorkflowConfig workflowConfig = null;
        if (StringUtils.isNotBlank(vs)) {
            Version version = Query.query(
                new VersionByIdQuery(WorkflowEntityConverter.versionId(entity.getWorkflowId(), vs))
            ).getVersion();
            workflowConfig = JacksonUtils.toType(version.getConfig(), WorkflowConfig.class);
        }
        return new WorkflowByIdQuery.Response(Workflow.of(entity.getWorkflowId(), workflowConfig));
    }


}
