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

import com.cronutils.model.CronType;
import io.fluxion.server.infrastructure.schedule.BasicCalculation;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;
import io.fluxion.server.infrastructure.schedule.ScheduleType;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * @author Devil
 */
public class ScheduledTaskFactory {

    /**
     * @param cron     cron表达式
     * @param cronType cron表达式类型 {@link CronType}
     */
    public static ScheduledTask cron(String id, String cron, String cronType, Consumer<ScheduledTask> consumer) {
        return cron(id, null, null, Duration.ZERO, cron, cronType, consumer);
    }

    /**
     * @param delay    延迟时间
     * @param cron     cron表达式
     * @param cronType cron表达式类型 {@link CronType}
     */
    public static ScheduledTask cron(String id, Long lastTriggerAt, Long lastFeedbackAt,
                                     Duration delay, String cron, String cronType, Consumer<ScheduledTask> consumer) {
        return task(id, lastTriggerAt, lastFeedbackAt,
            new ScheduleOption(ScheduleType.CRON, null, null, delay, null, cron, cronType),
            consumer
        );
    }

    public static ScheduledTask fixDelay(String id, Duration interval, Consumer<ScheduledTask> consumer) {
        return fixDelay(id, null, null, Duration.ZERO, interval, consumer);
    }

    public static ScheduledTask fixDelay(String id, Long lastTriggerAt, Long lastFeedbackAt,
                                         Duration delay, Duration interval, Consumer<ScheduledTask> consumer) {
        return task(id, lastTriggerAt, lastFeedbackAt,
            new ScheduleOption(ScheduleType.FIXED_DELAY, null, null, delay, interval, null, null),
            consumer
        );
    }

    public static ScheduledTask fixRate(String id, Duration interval, Consumer<ScheduledTask> consumer) {
        return fixRate(id, null, null, Duration.ZERO, interval, consumer);
    }

    public static ScheduledTask fixRate(String id, Long lastTriggerAt, Long lastFeedbackAt,
                                        Duration delay, Duration interval, Consumer<ScheduledTask> consumer) {
        return task(id, lastTriggerAt, lastFeedbackAt,
            new ScheduleOption(ScheduleType.FIXED_RATE, null, null, delay, interval, null, null),
            consumer
        );
    }

    public static ScheduledTask task(String id, Long lastTriggerAt, Long lastFeedbackAt,
                                     ScheduleOption scheduleOption, Consumer<ScheduledTask> consumer) {
        return new ScheduledTask(id, new BasicCalculation(
            lastTriggerAt, lastFeedbackAt, scheduleOption
        ), consumer);
    }

}
