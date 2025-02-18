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

package io.fluxion.server.core.trigger.handler;

import io.fluxion.server.core.trigger.query.ScheduleByBrokerQuery;
import io.fluxion.server.core.trigger.query.ScheduleByIdQuery;
import io.fluxion.server.infrastructure.dao.entity.ScheduleEntity;
import io.fluxion.server.infrastructure.dao.repository.ScheduleEntityRepo;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author Devil
 */
@Slf4j
@Component
public class ScheduleQueryHandler {

    @Resource
    private ScheduleEntityRepo scheduleEntityRepo;

    @QueryHandler
    public ScheduleByIdQuery.Response handle(ScheduleByIdQuery query) {
        ScheduleEntity entity = scheduleEntityRepo.findByScheduleIdAndDeleted(query.getId(), false);
        return new ScheduleByIdQuery.Response(ScheduleEntityConverter.convert(entity));
    }

    @QueryHandler
    public ScheduleByBrokerQuery.Response handle(ScheduleByBrokerQuery query) {
        List<ScheduleEntity> entities = scheduleEntityRepo.loadByBrokerAndUpdated(query.getBrokerId(), query.getUpdatedAt());
        return new ScheduleByBrokerQuery.Response(ScheduleEntityConverter.convert(entities));
    }

}
