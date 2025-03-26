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

package io.fluxion.server.infrastructure.schedule.task;

import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.server.infrastructure.schedule.BasicCalculation;
import io.fluxion.server.infrastructure.schedule.Calculable;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;
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
public class ScheduledTask extends AbstractTask {

    protected BasicCalculation calculation;

    /**
     * 业务逻辑
     */
    private final Consumer<ScheduledTask> consumer;

    public ScheduledTask(String id, BasicCalculation calculation, Consumer<ScheduledTask> consumer) {
        super(id);
        this.consumer = consumer;
        this.calculation = calculation;
    }

    @Override
    public void run() {
        consumer.accept(this);
    }

    @Override
    public LocalDateTime triggerAt() {
        return calculation.triggerAt();
    }

    public Calculable calculation() {
        return calculation;
    }

    public ScheduledTask nextTrigger() {
        LocalDateTime lastTriggerAt = calculation.lastTriggerAt();
        ScheduleOption scheduleOption = calculation.scheduleOption();
        calculation = new BasicCalculation(lastTriggerAt, TimeUtils.currentLocalDateTime(), scheduleOption);
        return this;
    }

}
