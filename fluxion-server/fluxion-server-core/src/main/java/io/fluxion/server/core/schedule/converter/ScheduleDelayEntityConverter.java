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

package io.fluxion.server.core.schedule.converter;

import io.fluxion.server.core.schedule.ScheduleDelay;
import io.fluxion.server.infrastructure.dao.entity.ScheduleDelayEntity;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
public class ScheduleDelayEntityConverter {

    public static ScheduleDelay convert(ScheduleDelayEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ScheduleDelay(
            convert(entity.getId()), ScheduleDelay.Status.parse(entity.getStatus())
        );
    }

    public static List<ScheduleDelay> convert(List<ScheduleDelayEntity> entities) {
        if (CollectionUtils.isEmpty(entities)) {
            return Collections.emptyList();
        }
        return entities.stream().map(ScheduleDelayEntityConverter::convert).collect(Collectors.toList());
    }

    public static List<ScheduleDelayEntity> convertToEntities(List<ScheduleDelay> delays) {
        if (CollectionUtils.isEmpty(delays)) {
            return Collections.emptyList();
        }
        return delays.stream().map(ScheduleDelayEntityConverter::convert).collect(Collectors.toList());
    }

    public static ScheduleDelayEntity convert(ScheduleDelay delay) {
        if (delay == null) {
            return null;
        }
        ScheduleDelayEntity entity = new ScheduleDelayEntity();
        entity.setId(convert(delay.getId()));
        entity.setDelayId(delay.getDelayId());
        entity.setStatus(delay.getStatus().value);
        return entity;
    }

    public static List<ScheduleDelayEntity.ID> convertToEntityIds(List<ScheduleDelay.ID> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return ids.stream().map(ScheduleDelayEntityConverter::convert).collect(Collectors.toList());
    }

    public static ScheduleDelay.ID convert(ScheduleDelayEntity.ID id) {
        return new ScheduleDelay.ID(
            id.getScheduleId(), id.getTriggerAt()
        );
    }

    public static ScheduleDelayEntity.ID convert(ScheduleDelay.ID id) {
        return new ScheduleDelayEntity.ID(
            id.getScheduleId(), id.getTriggerAt()
        );
    }

}
