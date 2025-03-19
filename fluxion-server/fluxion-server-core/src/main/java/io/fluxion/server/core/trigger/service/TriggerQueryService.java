/*
 * Copyright 2025-2030 limbo-io Team (https://github.com/limbo-io).
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

import io.fluxion.server.core.trigger.Trigger;
import io.fluxion.server.core.trigger.converter.TriggerEntityConverter;
import io.fluxion.server.core.trigger.query.TriggerByIdQuery;
import io.fluxion.server.infrastructure.dao.entity.TriggerEntity;
import io.fluxion.server.infrastructure.dao.repository.TriggerEntityRepo;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @author Devil
 */
@Service
public class TriggerQueryService {

    @Resource
    private TriggerEntityRepo triggerEntityRepo;

    @QueryHandler
    public TriggerByIdQuery.Response handle(TriggerByIdQuery query) {
        TriggerEntity entity = triggerEntityRepo.findById(query.getId()).orElse(null);
        Trigger trigger = TriggerEntityConverter.convert(entity);
        return new TriggerByIdQuery.Response(trigger);
    }
}
