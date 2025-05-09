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

import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.server.infrastructure.schedule.Calculable;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 固定间隔作业调度时间计算器
 *
 * @author Brozen
 * @since 2021-05-21
 */
@Slf4j
public class FixDelayScheduleCalculator implements ScheduleCalculator {

    /**
     * 通过此策略计算下一次触发调度的时间戳。如果不应该被触发，返回0或负数。
     *
     * @param calculable 待调度对象
     * @return 下次触发调度的时间戳，当返回非正数时，表示作业不会有触发时间。
     */
    @Override
    public LocalDateTime calculate(Calculable calculable) {
        ScheduleOption scheduleOption = calculable.scheduleOption();
        LocalDateTime lastFeedbackAt = calculable.lastFeedbackAt();
        // 如果为空，表示此次上次任务还没反馈，等待反馈后重新调度
        if (lastFeedbackAt == null) {
            // 如果上次触发为空表示这是第一次
            if (calculable.lastTriggerAt() == null) {
                LocalDateTime startScheduleAt = calculateStartScheduleTime(calculable.scheduleOption());
                return laterTime(startScheduleAt, TimeUtils.currentLocalDateTime());
            } else {
                return null;
            }
        }

        Duration interval = scheduleOption.getInterval();
        if (interval == null) {
            return TimeUtils.currentLocalDateTime();
        }

        LocalDateTime startScheduleAt = lastFeedbackAt.plus(interval);
        return laterTime(startScheduleAt, TimeUtils.currentLocalDateTime());
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.FIXED_DELAY;
    }


}
