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

package io.fluxion.server.infrastructure.schedule.schedule;

import io.fluxion.common.utils.time.Formatters;
import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import io.fluxion.server.infrastructure.schedule.scheduler.TaskScheduler;
import io.fluxion.server.infrastructure.schedule.task.ScheduledTask;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

/**
 * 可循环调度的task调度器
 *
 * @author Brozen
 * @since 2022-10-11
 */
@Slf4j
public class ScheduledTaskScheduler extends TaskScheduler<ScheduledTask> {

    public ScheduledTaskScheduler(Timer timer) {
        super(timer);
    }

    @Override
    protected Runnable run(ScheduledTask task) {
        return () -> {
            try {
                String scheduleId = task.id();

                ScheduleOption scheduleOption = task.calculation().scheduleOption();
                if (scheduleOption == null || scheduleOption.getType() == null || ScheduleType.UNKNOWN == scheduleOption.getType()) {
                    log.error("{} scheduleType is {} scheduleOption={}", scheduleId, ScheduleType.UNKNOWN.name(), scheduleOption);
                    return;
                }

                // 超过时间的不需要调度 todo @d 这里有问题 放到内存了直接停止了，没法重新加载
                LocalDateTime startTime = scheduleOption.getStartTime();
                LocalDateTime endTime = scheduleOption.getEndTime();
                LocalDateTime now = TimeUtils.currentLocalDateTime();
                if ((startTime != null && startTime.isAfter(now))
                    || (endTime != null && endTime.isBefore(now))) {
                    stop(task.id());
                    return;
                }

                switch (scheduleOption.getType()) {
                    case FIXED_RATE:
                    case CRON:
                        reschedule(task);
                        task.run();
                        break;
                    case FIXED_DELAY:
                        task.run();
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

    @Override
    protected void afterExecute(ScheduledTask task, Throwable thrown) {
        if (task.stopped()) {
            stop(task.id());
        }
    }

    private void reschedule(ScheduledTask task) {
        String scheduleId = task.id();
        try {
            log.info("reschedule task at:{}", task.triggerAt().format(Formatters.getFormatter(Formatters.YMD_HMS_SSS)));
            doSchedule(task.nextTrigger());
        } catch (Exception e) {
            log.error("ScheduledTask [{}] reschedule failed", scheduleId, e);
        }
    }

}
