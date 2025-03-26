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
import io.fluxion.server.core.workflow.WorkflowConfig;
import io.fluxion.server.core.workflow.converter.WorkflowEntityConverter;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.dao.entity.WorkflowEntity;
import io.fluxion.server.infrastructure.dao.repository.WorkflowEntityRepo;
import io.fluxion.server.infrastructure.utils.JpaHelper;
import io.fluxion.server.infrastructure.version.model.Version;
import io.fluxion.server.infrastructure.version.query.VersionByIdQuery;
import io.fluxion.server.start.api.workflow.request.WorkflowPageRequest;
import io.fluxion.server.start.api.workflow.view.WorkflowView;
import io.fluxion.server.start.converter.WorkflowConverter;
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
public class WorkflowService {

    @Resource
    private WorkflowEntityRepo workflowEntityRepo;

    public PageResponse<WorkflowView> page(WorkflowPageRequest request) {
        // 查询条件
        Specification<WorkflowEntity> condition = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(JpaHelper.equal(root, cb, WorkflowEntity::isDeleted, false));
            // 名称模糊查询
            String nameParam = request.getName();
            if (StringUtils.isNotBlank(nameParam)) {
                predicates.add(JpaHelper.like(root, cb, WorkflowEntity::getName, "%" + nameParam + "%"));
            }

            // 查询
            query.where(predicates.toArray(new Predicate[0]));
            // 排序
            query.orderBy(JpaHelper.desc(root, cb, WorkflowEntity::getWorkflowId));
            return query.getRestriction();
        };
        // 分页条件
        Pageable pageable = JpaHelper.pageable(request);
        Page<WorkflowEntity> queryResult = workflowEntityRepo.findAll(condition, pageable);
        List<WorkflowEntity> entities = queryResult.getContent();
        // 封装分页返回结果
        return request.response(queryResult.getTotalElements(), WorkflowConverter.toView(entities));
    }

    public WorkflowView get(String id, String versionId) {
        WorkflowEntity workflowEntity = workflowEntityRepo.findById(id).orElse(null);
        if (workflowEntity == null) {
            return null;
        }
        if (StringUtils.isBlank(versionId)) {
            versionId = StringUtils.defaultIfBlank(workflowEntity.getDraftVersion(), workflowEntity.getPublishVersion());
        }
        Version version = Query.query(
            new VersionByIdQuery(WorkflowEntityConverter.versionId(id, versionId))
        ).getVersion();

        WorkflowConfig workflowConfig = JacksonUtils.toType(version.getConfig(), WorkflowConfig.class);
        WorkflowView view = WorkflowConverter.toView(workflowEntity);
        view.setConfig(workflowConfig);
        return view;
    }

}
