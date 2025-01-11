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

package io.fluxion.core.schedule.calculator;

import io.fluxion.core.schedule.Scheduled;
import io.fluxion.core.schedule.ScheduleOption;
import io.fluxion.core.schedule.ScheduleType;
import io.fluxion.common.utils.time.TimeUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;

/**
 * 固定速度作业调度时间计算器
 *
 * @author Brozen
 * @since 2021-05-21
 */
@Slf4j
public class FixRateScheduleCalculator implements ScheduleCalculator {


    /**
     * 通过此策略计算下一次触发调度的时间戳。如果不应该被触发，返回0或负数。
     *
     * @param scheduled 待调度对象
     * @return 下次触发调度的时间戳，当返回非正数时，表示作业不会有触发时间。
     */
    @Override
    public Long calculate(Scheduled scheduled) {
        ScheduleOption scheduleOption = scheduled.scheduleOption();
        // 上次调度一定间隔后调度
        Duration interval = scheduleOption.getScheduleInterval();
        if (interval == null) {
            log.error("cannot calculate next trigger timestamp of {} because interval is not assigned!", scheduled);
            return ScheduleCalculator.NOT_TRIGGER;
        }

        // 如果上次为空则根据 delay 来
        if (scheduled.lastTriggerAt() == null) {
            Instant nowInstant = TimeUtils.currentInstant();
            long startScheduleAt = calculateStartScheduleTimestamp(scheduled.scheduleOption());
            return Math.max(startScheduleAt, nowInstant.getEpochSecond());
        }

        long now = TimeUtils.currentInstant().toEpochMilli();
        long scheduleAt = TimeUtils.toInstant(scheduled.lastTriggerAt()).toEpochMilli() + interval.toMillis();
        return Math.max(scheduleAt, now);
    }

    @Override
    public ScheduleType getScheduleType() {
        return ScheduleType.FIXED_RATE;
    }
}
