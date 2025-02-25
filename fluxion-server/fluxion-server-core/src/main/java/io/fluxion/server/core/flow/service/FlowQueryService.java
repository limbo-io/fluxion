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
import io.fluxion.server.core.flow.query.FlowByIdQuery;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.dao.entity.FlowEntity;
import io.fluxion.server.infrastructure.dao.repository.FlowEntityRepo;
import io.fluxion.server.infrastructure.exception.ErrorCode;
import io.fluxion.server.infrastructure.exception.PlatformException;
import io.fluxion.server.infrastructure.version.model.Version;
import io.fluxion.server.infrastructure.version.model.VersionRefType;
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
        String flowId = query.getFlowId();
        FlowEntity flowEntity = flowEntityRepo.findByFlowIdAndDeleted(flowId, false)
            .orElseThrow(() -> new PlatformException(ErrorCode.PARAM_ERROR, "flow not found flowId:" + flowId));
        if (StringUtils.isBlank(flowEntity.getRunVersion())) {
            throw new PlatformException(ErrorCode.PARAM_ERROR, "flow does not have runVersion flowId:" + flowId);
        }
        Version version = Query.query(VersionByIdQuery.builder()
            .refId(flowEntity.getFlowId())
            .refType(VersionRefType.FLOW)
            .version(flowEntity.getRunVersion())
            .build()
        ).getVersion();
        if (version == null) {
            throw new PlatformException(ErrorCode.PARAM_ERROR, "flow does not find runVersion flowId:" + flowId + " version:" + flowEntity.getRunVersion());

        }
        FlowConfig flowConfig = JacksonUtils.toType(version.getConfig(), FlowConfig.class);
        return new FlowByIdQuery.Response(Flow.of(flowEntity.getFlowId(), flowConfig));
    }
}
