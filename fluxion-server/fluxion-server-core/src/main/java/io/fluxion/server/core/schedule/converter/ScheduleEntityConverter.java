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
import io.fluxion.server.infrastructure.schedule.MisfirePolicy;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Schedule 配置态（本地日历墙钟 + IANA timeZone，见 CONTEXT.md「统一时间模型」）与
 * 持久化运行态（绝对 Instant，UTC DATETIME(3)）之间的转换边界：
 * 配置墙钟按 schedule 的 timeZone 解释为绝对时刻；epoch/DST 规则由计算器负责，本层只做解释。
 *
 * @author Devil
 */
public class ScheduleEntityConverter {

    public static List<Schedule> convert(List<ScheduleEntity> entities) {
        if (CollectionUtils.isEmpty(entities)) {
            return Collections.emptyList();
        }
        return entities.stream().map(ScheduleEntityConverter::convert).collect(Collectors.toList());
    }

    public static void assemble(ScheduleEntity entity, ScheduleOption scheduleOption) {
        ZoneId tz = scheduleOption.getTimeZone();
        entity.setScheduleType(scheduleOption.getType().value);
        entity.setStartTime(toAbsolute(scheduleOption.getStartTime(), tz));
        entity.setEndTime(toAbsolute(scheduleOption.getEndTime(), tz));
        entity.setScheduleDelay(scheduleOption.getDelay() == null ? 0L : scheduleOption.getDelay().getSeconds());
        entity.setScheduleInterval(scheduleOption.getInterval() == null ? 0L : scheduleOption.getInterval().getSeconds());
        entity.setScheduleCron(scheduleOption.getCron() == null ? StringUtils.EMPTY : scheduleOption.getCron());
        entity.setScheduleCronType(scheduleOption.getCronType() == null ? StringUtils.EMPTY : scheduleOption.getCronType());
        entity.setTimeZone(scheduleOption.getTimeZone().getId());
        entity.setMisfirePolicy(scheduleOption.getMisfirePolicy().name());
        entity.setMaxFireAttempts(scheduleOption.getMaxFireAttempts());
        entity.setMaxStartDelayMillis(scheduleOption.getMaxStartDelayMillis());
    }

    public static ScheduleEntity convert(Schedule schedule) {
        if (schedule == null) {
            return null;
        }
        ScheduleOption scheduleOption = schedule.getOption();
        ScheduleEntity entity = new ScheduleEntity();
        entity.setScheduleId(schedule.getId());
        assemble(entity, scheduleOption);
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
        schedule.setOption(toOption(entity));
        schedule.setLastTriggerAt(entity.getLastTriggerAt());
        schedule.setLastFeedbackAt(entity.getLastFeedbackAt());
        schedule.setNextTriggerAt(entity.getNextTriggerAt());
        schedule.setEnabled(entity.isEnabled());
        return schedule;
    }

    public static ScheduleOption toOption(ScheduleEntity entity) {
        ZoneId tz = entity.getTimeZone() == null ? ZoneId.of("UTC") : ZoneId.of(entity.getTimeZone());
        return new ScheduleOption(
            ScheduleType.parse(entity.getScheduleType()),
            toWallClock(entity.getStartTime(), tz),
            toWallClock(entity.getEndTime(), tz),
            Duration.ofSeconds(entity.getScheduleDelay()),
            Duration.ofSeconds(entity.getScheduleInterval()),
            entity.getScheduleCron(),
            entity.getScheduleCronType(),
            entity.getMisfirePolicy() == null ? null : MisfirePolicy.valueOf(entity.getMisfirePolicy()),
            entity.getMaxFireAttempts(),
            tz.getId(),
            entity.getMaxStartDelayMillis()
        );
    }

    /**
     * 配置墙钟 → 绝对时刻：按 schedule 的 timeZone 解释；null 语义上代表“未配置起始时刻”，
     * 原实现取当前时间，这里保持同义（以配置时区当前时刻）。
     */
    private static Instant toAbsolute(LocalDateTime wallClock, ZoneId tz) {
        if (wallClock == null) {
            return Instant.now();
        }
        return wallClock.atZone(tz).toInstant();
    }

    /** 绝对时刻 → 配置墙钟（按 schedule 的 timeZone 展示）。 */
    private static LocalDateTime toWallClock(Instant instant, ZoneId tz) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, tz);
    }

}