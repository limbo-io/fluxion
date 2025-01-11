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

package io.fluxion.core.schedule.scheduler;

import io.fluxion.core.schedule.ScheduleOption;
import io.fluxion.core.schedule.ScheduleType;
import io.fluxion.core.schedule.task.ScheduledTask;
import io.fluxion.common.utils.time.Formatters;
import io.fluxion.common.utils.time.TimeUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 可循环调度的task调度器
 *
 * @author Brozen
 * @since 2022-10-11
 */
@Slf4j
public class ScheduledTaskScheduler extends TaskScheduler<ScheduledTask> {

    public ScheduledTaskScheduler(long tickDuration, TimeUnit unit) {
        super(tickDuration, unit);
    }

    @Override
    protected Consumer<ScheduledTask> consumer() {
        return task -> {
            try {
                String scheduleId = task.id();
                ScheduleOption scheduleOption = task.scheduleOption();
                if (scheduleOption == null || scheduleOption.getScheduleType() == null || ScheduleType.UNKNOWN == scheduleOption.getScheduleType()) {
                    log.error("{} scheduleType is {} scheduleOption={}", scheduleId, ScheduleType.UNKNOWN.name(), scheduleOption);
                    return;
                }

                switch (scheduleOption.getScheduleType()) {
                    case FIXED_RATE:
                    case CRON:
                        reschedule(task);
                        task.execute();
                        break;
                    case FIXED_DELAY:
                        task.execute();
                        reschedule(task);
                        break;
                    default:
                        break;
                }
            } catch (Exception e) {
                log.error("[ScheduledTaskScheduler] schedule fail id:{}", task.id(), e);
            }
        };
    }

    public void reschedule(ScheduledTask task) {
        String scheduleId = task.id();
        try {
            log.error("reschedule task at:{}", task.triggerAt().format(Formatters.getFormatter(Formatters.YMD_HMS_SSS)));
            calAndSchedule(new ScheduledTask(
                    task.id(),
                    task.triggerAt(), TimeUtils.currentLocalDateTime(), task.scheduleOption(),
                    task.consumer()
            ), consumer());
        } catch (Exception e) {
            log.error("ScheduledTask [{}] reschedule failed", scheduleId, e);
        }
    }

}
