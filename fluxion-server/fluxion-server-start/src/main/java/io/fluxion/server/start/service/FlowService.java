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

package io.fluxion.server.start.service;

import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.remote.core.api.PageResponse;
import io.fluxion.server.core.flow.FlowConfig;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.dao.entity.FlowEntity;
import io.fluxion.server.infrastructure.dao.repository.FlowEntityRepo;
import io.fluxion.server.infrastructure.utils.JpaHelper;
import io.fluxion.server.infrastructure.version.model.Version;
import io.fluxion.server.infrastructure.version.model.VersionRefType;
import io.fluxion.server.infrastructure.version.query.VersionByIdQuery;
import io.fluxion.server.start.api.flow.request.FlowPageRequest;
import io.fluxion.server.start.api.flow.view.FlowView;
import io.fluxion.server.start.converter.FlowConverter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Devil
 */
@Service
public class FlowService {

    @Resource
    private FlowEntityRepo flowEntityRepo;

    public PageResponse<FlowView> page(FlowPageRequest request) {
        // 查询条件
        Specification<FlowEntity> condition = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(JpaHelper.equal(root, cb, FlowEntity::isDeleted, false));
            // 名称模糊查询
            String nameParam = request.getName();
            if (StringUtils.isNotBlank(nameParam)) {
                predicates.add(JpaHelper.like(root, cb, FlowEntity::getName, "%" + nameParam + "%"));
            }

            // 查询
            query.where(predicates.toArray(new Predicate[0]));
            // 排序
            query.orderBy(JpaHelper.desc(root, cb, FlowEntity::getFlowId));
            return query.getRestriction();
        };
        // 分页条件
        Pageable pageable = JpaHelper.pageable(request);
        Page<FlowEntity> queryResult = flowEntityRepo.findAll(condition, pageable);
        List<FlowEntity> entities = queryResult.getContent();
        // 封装分页返回结果
        return request.response(queryResult.getTotalElements(), FlowConverter.toView(entities));
    }

    public FlowView get(String flowId, String versionId) {
        FlowEntity flowEntity = flowEntityRepo.findById(flowId).orElse(null);
        if (flowEntity == null) {
            return null;
        }
        if (StringUtils.isBlank(versionId)) {
            versionId = StringUtils.defaultString(flowEntity.getDraftVersion(), flowEntity.getRunVersion());
        }
        Version version = Query.query(VersionByIdQuery.builder()
            .refId(flowId)
            .refType(VersionRefType.FLOW)
            .version(versionId)
            .build()
        ).getVersion();

        FlowConfig flowConfig = JacksonUtils.toType(version.getConfig(), FlowConfig.class);
        FlowView view = FlowConverter.toView(flowEntity);
        view.setConfig(flowConfig);
        return view;
    }

}
