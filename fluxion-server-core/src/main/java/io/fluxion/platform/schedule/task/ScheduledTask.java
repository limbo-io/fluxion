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

package io.fluxion.platform.schedule.task;

import io.fluxion.platform.schedule.Scheduled;
import io.fluxion.platform.schedule.ScheduleOption;
import io.fluxion.platform.schedule.calculator.ScheduleCalculatorFactory;
import io.fluxion.common.utils.time.TimeUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.function.Consumer;

/**
 * 定时任务 可循环执行
 * 到指定时间点创建实例
 *
 * @author Devil
 * @since 2022/12/19
 */
@Slf4j
public class ScheduledTask extends AbstractTask implements Scheduled {

    /**
     * 上次任务触发时间
     */
    private final LocalDateTime lastTriggerAt;

    /**
     * 下次任务触发时间
     */
    private final LocalDateTime nextTriggerAt;

    /**
     * 上次调度反馈的时间
     */
    private final LocalDateTime lastFeedbackAt;

    /**
     * 调度配置
     */
    private final ScheduleOption scheduleOption;

    /**
     * 业务逻辑
     */
    private final Consumer<ScheduledTask> consumer;

    public ScheduledTask(String id, LocalDateTime lastTriggerAt, LocalDateTime lastFeedbackAt,
                         ScheduleOption scheduleOption, Consumer<ScheduledTask> consumer) {
        super(id);
        this.lastTriggerAt = lastTriggerAt;
        this.lastFeedbackAt = lastFeedbackAt;
        this.scheduleOption = scheduleOption;
        this.nextTriggerAt = calNextTriggerAt();
        this.consumer = consumer;
    }

    @Override
    public void execute() {
        consumer.accept(this);
    }

    public Consumer<ScheduledTask> consumer() {
        return consumer;
    }

    @Override
    public LocalDateTime triggerAt() {
        return nextTriggerAt;
    }

    @Override
    public ScheduleOption scheduleOption() {
        return scheduleOption;
    }

    @Override
    public LocalDateTime lastTriggerAt() {
        return lastTriggerAt;
    }

    /**
     * 下次触发时间
     */
    public LocalDateTime calNextTriggerAt() {
        Long calculate = ScheduleCalculatorFactory.create(scheduleOption.getScheduleType()).calculate(this);
        return TimeUtils.toLocalDateTime(calculate);
    }

    @Override
    public LocalDateTime lastFeedbackAt() {
        return lastFeedbackAt;
    }

}
