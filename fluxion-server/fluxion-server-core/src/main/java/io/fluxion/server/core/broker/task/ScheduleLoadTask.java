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
import io.fluxion.server.core.trigger.TriggerHelper;
import io.fluxion.server.core.trigger.cmd.ScheduleRefreshLastTriggerCmd;
import io.fluxion.server.core.trigger.query.ScheduleUpdatedQuery;
import io.fluxion.server.core.trigger.run.Schedule;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.schedule.Calculable;
import io.fluxion.server.infrastructure.schedule.schedule.DelayedTaskScheduler;
import io.fluxion.server.infrastructure.schedule.schedule.ScheduledTaskScheduler;
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
            // todo @d 由于Schedule一直在更新这个方式有点问题会一直拉数据
            List<Schedule> schedules = Query.query(new ScheduleUpdatedQuery(brokerId, loadTimePoint.plusSeconds(-interval))).getSchedules();
            loadTimePoint = now;
            for (Schedule schedule : schedules) {
                if (schedule == null) {
                    continue;
                }
                String scheduleId = TriggerHelper.taskScheduleId(schedule);
                // 移除老的，调度新的
                switch (schedule.getScheduleType()) {
                    case CRON:
                    case FIXED_RATE:
                        ScheduledTaskScheduler scheduledTaskScheduler = BrokerContext.broker().scheduledTaskScheduler();
                        scheduledTaskScheduler.schedule(ScheduledTaskFactory.task(
                            scheduleId,
                            schedule.getLastTriggerAt(),
                            schedule.getLastFeedbackAt(),
                            schedule.getScheduleOption(),
                            scheduledTask -> TriggerHelper.consumerTask(scheduledTask, schedule.getId())
                        ));
                        break;
                    case FIXED_DELAY:
                        DelayedTaskScheduler delayedTaskScheduler = BrokerContext.broker().delayedTaskScheduler();
                        delayedTaskScheduler.schedule(DelayedTaskFactory.create(
                            scheduleId,
                            schedule.getLastTriggerAt(),
                            schedule.getLastFeedbackAt(),
                            schedule.getScheduleOption(),
                            delayedTask -> TriggerHelper.consumerTask(delayedTask, schedule.getId())
                        ));
                        break;
                }
            }
        } catch (Exception e) {
            log.error("[{}] execute fail", this.getClass().getSimpleName(), e);
        }
    }

}
