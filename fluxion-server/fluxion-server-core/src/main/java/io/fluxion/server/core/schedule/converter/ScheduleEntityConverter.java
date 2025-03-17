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

import io.fluxion.server.core.schedule.Schedule;
import io.fluxion.server.infrastructure.dao.entity.ScheduleEntity;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import org.apache.commons.collections4.CollectionUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
public class ScheduleEntityConverter {

    public static List<Schedule> convert(List<ScheduleEntity> entities) {
        if (CollectionUtils.isEmpty(entities)) {
            return Collections.emptyList();
        }
        return entities.stream().map(ScheduleEntityConverter::convert).collect(Collectors.toList());
    }

    public static ScheduleEntity convert(Schedule schedule) {
        if (schedule == null) {
            return null;
        }
        ScheduleOption scheduleOption = schedule.getOption();
        ScheduleEntity entity = new ScheduleEntity();
        entity.setScheduleId(schedule.getId());
        entity.setScheduleType(scheduleOption.getType().value);
        entity.setStartTime(scheduleOption.getStartTime());
        entity.setEndTime(scheduleOption.getEndTime());
        entity.setScheduleDelay(scheduleOption.getDelay().getSeconds());
        entity.setScheduleInterval(scheduleOption.getInterval().getSeconds());
        entity.setScheduleCron(scheduleOption.getCron());
        entity.setScheduleCronType(scheduleOption.getCronType());
        entity.setNextTriggerAt(schedule.getNextTriggerAt());
        entity.setLastFeedbackAt(schedule.getLastFeedbackAt());
        entity.setLastTriggerAt(schedule.getLastTriggerAt());
        entity.setEnabled(schedule.isEnabled());
        return entity;
    }

    public static Schedule convert(ScheduleEntity entity) {
        if (entity == null) {
            return null;
        }
        Schedule schedule = new Schedule();
        schedule.setId(entity.getScheduleId());
        schedule.setBrokerId(entity.getBrokerId());
        schedule.setOption(toOption(entity));
        schedule.setLastTriggerAt(entity.getLastTriggerAt());
        schedule.setLastFeedbackAt(entity.getLastFeedbackAt());
        schedule.setNextTriggerAt(entity.getNextTriggerAt());
        schedule.setEnabled(entity.isEnabled());
        return schedule;
    }

    public static ScheduleOption toOption(ScheduleEntity entity) {
        return new ScheduleOption(
            ScheduleType.parse(entity.getScheduleType()),
            entity.getStartTime(),
            entity.getEndTime(),
            Duration.ofMillis(entity.getScheduleDelay()),
            Duration.ofMillis(entity.getScheduleInterval()),
            entity.getScheduleCron(),
            entity.getScheduleCronType()
        );
    }

}
