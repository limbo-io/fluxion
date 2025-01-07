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

import com.cronutils.model.CronType;
import io.fluxion.platform.schedule.ScheduleOption;
import io.fluxion.platform.schedule.ScheduleType;

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
        return cron(id, Duration.ZERO, cron, cronType, consumer);
    }

    /**
     * @param delay    延迟时间
     * @param cron     cron表达式
     * @param cronType cron表达式类型 {@link CronType}
     */
    public static ScheduledTask cron(String id, Duration delay, String cron, String cronType, Consumer<ScheduledTask> consumer) {
        return task(id,
                new ScheduleOption(ScheduleType.CRON, null, null, delay, null, cron, cronType),
                consumer
        );
    }

    public static ScheduledTask fixDelay(String id, Duration interval, Consumer<ScheduledTask> consumer) {
        return fixDelay(id, Duration.ZERO, interval, consumer);
    }

    public static ScheduledTask fixDelay(String id, Duration delay, Duration interval, Consumer<ScheduledTask> consumer) {
        return task(id,
                new ScheduleOption(ScheduleType.FIXED_DELAY, null, null, delay, interval, null, null),
                consumer
        );
    }

    public static ScheduledTask fixRate(String id, Duration interval, Consumer<ScheduledTask> consumer) {
        return fixRate(id, Duration.ZERO, interval, consumer);
    }

    public static ScheduledTask fixRate(String id, Duration delay, Duration interval, Consumer<ScheduledTask> consumer) {
        return task(id,
                new ScheduleOption(ScheduleType.FIXED_RATE, null, null, delay, interval, null, null),
                consumer
        );
    }

    public static ScheduledTask task(String id, ScheduleOption scheduleOption, Consumer<ScheduledTask> consumer) {
        return new ScheduledTask(id, null, null, scheduleOption, consumer);
    }

}
