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

package io.fluxion.server.infrastructure.schedule;

import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.server.infrastructure.schedule.calculator.ScheduleCalculator;
import io.fluxion.server.infrastructure.schedule.calculator.ScheduleCalculatorFactory;

import java.time.LocalDateTime;

/**
 * 计算过程
 *
 * @author Devil
 * @since 2022/8/8
 */
public class BasicCalculation implements Calculable {

    /**
     * 上次任务触发时间
     */
    private final LocalDateTime lastTriggerAt;

    /**
     * 上次调度反馈的时间
     */
    private final LocalDateTime lastFeedbackAt;

    /**
     * 调度配置
     */
    private final ScheduleOption scheduleOption;

    /**
     * 计算器
     */
    private final ScheduleCalculator calculator;

    /**
     * 计算结果，执行时间
     */
    private final LocalDateTime triggerAt;

    public BasicCalculation(LocalDateTime lastTriggerAt, LocalDateTime lastFeedbackAt,
                            ScheduleOption scheduleOption) {
        this.lastTriggerAt = lastTriggerAt;
        this.lastFeedbackAt = lastFeedbackAt;
        this.scheduleOption = scheduleOption;
        this.calculator = ScheduleCalculatorFactory.create(scheduleOption.getScheduleType());
        this.triggerAt = calTriggerAt();
    }

    /**
     * 下次触发时间
     */
    private LocalDateTime calTriggerAt() {
        Long calculate = calculator.calculate(this);
        return TimeUtils.toLocalDateTime(calculate);
    }

    public LocalDateTime triggerAt() {
        return triggerAt;
    }


    @Override
    public ScheduleOption scheduleOption() {
        return scheduleOption;
    }

    @Override
    public LocalDateTime lastTriggerAt() {
        return lastTriggerAt;
    }

    @Override
    public LocalDateTime lastFeedbackAt() {
        return lastFeedbackAt;
    }
}
