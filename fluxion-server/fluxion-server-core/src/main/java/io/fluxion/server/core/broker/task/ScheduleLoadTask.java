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

package io.fluxion.server.core.broker.task;

import io.fluxion.common.utils.time.Formatters;
import io.fluxion.common.utils.time.LocalDateTimeUtils;
import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.execution.Execution;
import io.fluxion.server.core.execution.cmd.ExecutionCreateCmd;
import io.fluxion.server.core.execution.cmd.ExecutionRunCmd;
import io.fluxion.server.core.trigger.query.ScheduleByIdQuery;
import io.fluxion.server.core.trigger.query.ScheduleUpdatedQuery;
import io.fluxion.server.core.trigger.run.Schedule;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.schedule.schedule.DelayedTaskScheduler;
import io.fluxion.server.infrastructure.schedule.schedule.ScheduledTaskScheduler;
import io.fluxion.server.infrastructure.schedule.task.AbstractTask;
import io.fluxion.server.infrastructure.schedule.task.DelayedTaskFactory;
import io.fluxion.server.infrastructure.schedule.task.ScheduledTaskFactory;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 加载 ScheduledTask 并执行
 *
 * @author Devil
 */
@Slf4j
public class ScheduleLoadTask extends CoreTask {

    private LocalDateTime loadTimePoint = LocalDateTimeUtils.parse("2000-01-01 00:00:00", Formatters.YMD_HMS);

    public ScheduleLoadTask(int interval, TimeUnit unit) {
        super(interval, unit);
    }

    @Override
    public void run() {
        String brokerId = BrokerContext.broker().id();
        try {
            LocalDateTime now = TimeUtils.currentLocalDateTime();
            List<Schedule> schedules = Query.query(new ScheduleUpdatedQuery(brokerId, loadTimePoint.plusSeconds(-interval))).getSchedules();
            loadTimePoint = now;
            for (Schedule schedule : schedules) {
                if (schedule == null) {
                    continue;
                }
                String scheduleId = scheduleId(schedule);
                // 移除老的，调度新的
                switch (schedule.getScheduleType()) {
                    case CRON:
                    case FIXED_RATE:
                        ScheduledTaskScheduler scheduledTaskScheduler = BrokerContext.broker().scheduledTaskScheduler();
                        scheduledTaskScheduler.schedule(ScheduledTaskFactory.task(
                            scheduleId,
                            schedule.getLatelyTriggerAt(),
                            schedule.getLatelyFeedbackAt(),
                            schedule.getScheduleOption(),
                            scheduledTask -> consumerTask(scheduledTask, schedule.getId())
                        ));
                        break;
                    case FIXED_DELAY:
                        DelayedTaskScheduler delayedTaskScheduler = BrokerContext.broker().delayedTaskScheduler();
                        delayedTaskScheduler.schedule(DelayedTaskFactory.create(
                            scheduleId,
                            schedule.getLatelyTriggerAt(),
                            schedule.getLatelyFeedbackAt(),
                            schedule.getScheduleOption(),
                            delayedTask -> consumerTask(delayedTask, schedule.getId())
                        ));
                        break;
                }
            }
        } catch (Exception e) {
            log.error("[{}] execute fail", this.getClass().getSimpleName(), e);
        }
    }

    private void consumerTask(AbstractTask task, String scheduleId) {
        // 移除不需要调度的
        Schedule schedule = Query.query(new ScheduleByIdQuery(scheduleId)).getSchedule();
        if (!schedule.isEnabled()) {
            task.stop();
            return;
        }
        // 版本变化了老的可以不用执行
        if (!task.id().equals(scheduleId(schedule))) {
            task.stop();
            return;
        }
        Execution execution = Cmd.send(new ExecutionCreateCmd(
            schedule.getRefId(),
            schedule.getRefType(),
            task.triggerAt()
        )).getExecution();
        Cmd.send(new ExecutionRunCmd(execution));
    }

    private String scheduleId(Schedule schedule) {
        // entity = trigger 所以不会变，这里使用version判断是否有版本变动
        return "st_" + schedule.getId() + "_" + schedule.getVersion();
    }

}
