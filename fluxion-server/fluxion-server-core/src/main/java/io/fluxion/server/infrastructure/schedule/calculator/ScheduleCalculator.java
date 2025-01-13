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

package io.fluxion.server.infrastructure.schedule.calculator;

import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import io.fluxion.server.infrastructure.schedule.Scheduled;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 调度时间计算策略，用于计算下次触发调度时间戳
 *
 * @author Brozen
 * @since 2021-05-20
 */
public interface ScheduleCalculator {

    /**
     * 没有下次触发时间时，返回负数
     */
    long NOT_TRIGGER = -1;


    /**
     * 通过此策略计算下一次触发调度的时间戳。如果不应该被触发，返回负数。
     * @param scheduled 可计算的对象
     * @return 下次触发调度的时间戳，当返回负数时，表示作业不会有触发时间。
     */
    Long calculate(Scheduled scheduled);
    /**
     * 此策略适用的调度类型
     */
    ScheduleType getScheduleType();

    /**
     * 计算作业的开始调度时间，从作业创建时间开始，加上delay。
     * @param scheduleOption 作业调度配置
     * @return 作业开始进行调度计算的时间
     */
    default long calculateStartScheduleTimestamp(ScheduleOption scheduleOption) {
        LocalDateTime startAt = scheduleOption.getScheduleStartAt();
        Duration delay = scheduleOption.getScheduleDelay();
        long startScheduleAt = startAt.toInstant(TimeUtils.defaultZoneOffset()).toEpochMilli();
        return delay != null ? startScheduleAt + delay.toMillis() : startScheduleAt;
    }

}
