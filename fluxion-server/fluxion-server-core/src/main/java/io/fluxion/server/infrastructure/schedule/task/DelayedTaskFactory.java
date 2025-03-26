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

import io.fluxion.server.infrastructure.schedule.BasicCalculation;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;

import java.util.function.Consumer;

/**
 * @author Devil
 */
public class DelayedTaskFactory {

    public static DelayedTask create(String id, Long lastFeedbackAt,
                                     ScheduleOption scheduleOption, Consumer<DelayedTask> consumer) {
        BasicCalculation calculation = new BasicCalculation(
            null, lastFeedbackAt, scheduleOption
        );
        return new DelayedTask(id, calculation.triggerAt(), consumer);
    }

    public static DelayedTask create(String id, Long triggerAt, Consumer<DelayedTask> consumer) {
        return new DelayedTask(id, triggerAt, consumer);
    }

}
