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

package io.fluxion.server.core.trigger.service;

import io.fluxion.server.core.broker.BrokerManger;
import io.fluxion.server.core.broker.BrokerNode;
import io.fluxion.server.core.trigger.converter.ScheduleEntityConverter;
import io.fluxion.server.core.trigger.query.ScheduleByIdQuery;
import io.fluxion.server.core.trigger.query.ScheduleNotOwnerQuery;
import io.fluxion.server.core.trigger.query.ScheduleUpdatedQuery;
import io.fluxion.server.infrastructure.dao.entity.ScheduleEntity;
import io.fluxion.server.infrastructure.dao.repository.ScheduleEntityRepo;
import io.fluxion.server.infrastructure.utils.JpaHelper;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
@Slf4j
@Service
public class ScheduleQueryService {

    @Resource
    private ScheduleEntityRepo scheduleEntityRepo;

    @Resource
    private BrokerManger brokerManger;

    @QueryHandler
    public ScheduleByIdQuery.Response handle(ScheduleByIdQuery query) {
        ScheduleEntity entity = scheduleEntityRepo.findByScheduleIdAndDeleted(query.getId(), false);
        return new ScheduleByIdQuery.Response(ScheduleEntityConverter.convert(entity));
    }

    @QueryHandler
    public ScheduleUpdatedQuery.Response handle(ScheduleUpdatedQuery query) {
        List<ScheduleEntity> entities = scheduleEntityRepo.loadByBrokerAndUpdated(query.getBrokerId(), query.getUpdatedAt());
        return new ScheduleUpdatedQuery.Response(ScheduleEntityConverter.convert(entities));
    }

    @QueryHandler
    public ScheduleNotOwnerQuery.Response handle(ScheduleNotOwnerQuery query) {
        Pageable pageable = JpaHelper.pageable(1, query.getLimit());
        List<BrokerNode> brokerNodes = brokerManger.allAlive();
        List<String> brokerIds = brokerNodes.stream().map(BrokerNode::id).collect(Collectors.toList());
        Page<ScheduleEntity> page = scheduleEntityRepo.findByBrokerIdNotInAndDeletedAndEnabledContaining(brokerIds, false, true, pageable);
        return new ScheduleNotOwnerQuery.Response(ScheduleEntityConverter.convert(page.toList()));
    }

}
