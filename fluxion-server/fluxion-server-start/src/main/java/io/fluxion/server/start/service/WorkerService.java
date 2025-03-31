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

import io.fluxion.remote.core.api.PageResponse;
import io.fluxion.server.core.worker.Worker;
import io.fluxion.server.core.worker.query.WorkerByIdsQuery;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.dao.entity.WorkerEntity;
import io.fluxion.server.infrastructure.dao.repository.WorkerEntityRepo;
import io.fluxion.server.infrastructure.utils.JpaHelper;
import io.fluxion.server.start.api.worker.request.WorkerPageRequest;
import io.fluxion.server.start.api.worker.view.WorkerView;
import io.fluxion.server.start.converter.WorkerConverter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
@Service
public class WorkerService {

    @Resource
    private WorkerEntityRepo workerEntityRepo;

    public PageResponse<WorkerView> page(WorkerPageRequest request) {
        // 查询条件
        Specification<WorkerEntity> condition = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(JpaHelper.equal(root, cb, WorkerEntity::isDeleted, false));

            // app查询
            if (StringUtils.isNotBlank(request.getAppId())) {
                predicates.add(JpaHelper.equal(root, cb, WorkerEntity::getAppId, request.getAppId()));
            }

            // 查询
            query.where(predicates.toArray(new Predicate[0]));
            // 排序
            query.orderBy(JpaHelper.desc(root, cb, WorkerEntity::getWorkerId));
            // 只查询固定字段
            query.select(root.get("workerId"));
            return query.getRestriction();
        };
        // 分页条件
        Pageable pageable = JpaHelper.pageable(request);
        Page<WorkerEntity> queryResult = workerEntityRepo.findAll(condition, pageable);
        List<WorkerEntity> entities = queryResult.getContent();
        List<String> ids = entities.stream().map(WorkerEntity::getWorkerId).collect(Collectors.toList());
        List<Worker> workers = Query.query(new WorkerByIdsQuery(ids)).getWorkers();
        // 封装分页返回结果
        return request.response(queryResult.getTotalElements(), WorkerConverter.toView(workers));
    }

}
