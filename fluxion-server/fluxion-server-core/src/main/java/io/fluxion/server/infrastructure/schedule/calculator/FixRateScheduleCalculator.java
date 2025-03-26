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

package io.fluxion.server.infrastructure.schedule.calculator;

import io.fluxion.server.infrastructure.schedule.Calculable;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

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
     * @param calculable 待调度对象
     * @return 下次触发调度的时间戳，当返回非正数时，表示作业不会有触发时间。
     */
    @Override
    public Long calculate(Calculable calculable) {
        ScheduleOption scheduleOption = calculable.scheduleOption();
        // 上次调度一定间隔后调度
        Duration interval = scheduleOption.getInterval();
        if (interval == null) {
            log.error("cannot calculate next trigger timestamp of {} because interval is not assigned!", calculable);
            return ScheduleCalculator.NOT_TRIGGER;
        }

        // 如果上次为空则根据 delay 来
        if (calculable.lastTriggerAt() == null) {
            long startScheduleAt = calculateStartScheduleTimestamp(calculable.scheduleOption());
            return Math.max(startScheduleAt, System.currentTimeMillis());
        }

        long scheduleAt = calculable.lastTriggerAt() + interval.toMillis();
        return Math.max(scheduleAt, System.currentTimeMillis());
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.FIXED_RATE;
    }
}
