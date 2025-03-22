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
import io.fluxion.server.core.trigger.Trigger;
import io.fluxion.server.core.trigger.query.TriggerByIdsQuery;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.dao.entity.TriggerEntity;
import io.fluxion.server.infrastructure.dao.repository.TriggerEntityRepo;
import io.fluxion.server.infrastructure.utils.JpaHelper;
import io.fluxion.server.start.api.trigger.request.TriggerPageRequest;
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
public class TriggerService {

    @Resource
    private TriggerEntityRepo triggerEntityRepo;

    public PageResponse<Trigger> page(TriggerPageRequest request) {
        // 查询条件
        Specification<TriggerEntity> condition = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(JpaHelper.equal(root, cb, TriggerEntity::isDeleted, false));

            // 查询
            query.where(predicates.toArray(new Predicate[0]));
            // 排序
            query.orderBy(JpaHelper.desc(root, cb, TriggerEntity::getTriggerId));
            // 只查询固定字段
            query.select(root.get("triggerId"));
            return query.getRestriction();
        };
        // 分页条件
        Pageable pageable = JpaHelper.pageable(request);
        Page<TriggerEntity> queryResult = triggerEntityRepo.findAll(condition, pageable);
        List<TriggerEntity> entities = queryResult.getContent();
        List<String> ids = entities.stream().map(TriggerEntity::getTriggerId).collect(Collectors.toList());
        List<Trigger> triggers = Query.query(new TriggerByIdsQuery(ids)).getTriggers();
        // 封装分页返回结果
        return request.response(queryResult.getTotalElements(), triggers);
    }

}
