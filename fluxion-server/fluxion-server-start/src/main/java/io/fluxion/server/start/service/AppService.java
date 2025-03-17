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
import io.fluxion.server.infrastructure.dao.entity.AppEntity;
import io.fluxion.server.infrastructure.dao.repository.AppEntityRepo;
import io.fluxion.server.infrastructure.utils.JpaHelper;
import io.fluxion.server.start.api.app.request.AppPageRequest;
import io.fluxion.server.start.api.app.view.AppView;
import io.fluxion.server.start.converter.AppConverter;
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
public class AppService {

    @Resource
    private AppEntityRepo appEntityRepo;

    public PageResponse<AppView> page(AppPageRequest request) {
        // 查询条件
        Specification<AppEntity> condition = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(JpaHelper.equal(root, cb, AppEntity::isDeleted, false));

            // 查询
            query.where(predicates.toArray(new Predicate[0]));
            // 排序
            query.orderBy(JpaHelper.desc(root, cb, AppEntity::getAppId));
            return query.getRestriction();
        };
        // 分页条件
        Pageable pageable = JpaHelper.pageable(request);
        Page<AppEntity> queryResult = appEntityRepo.findAll(condition, pageable);
        List<AppEntity> entities = queryResult.getContent();
        // 封装分页返回结果
        return request.response(queryResult.getTotalElements(), AppConverter.toView(entities));
    }

}
