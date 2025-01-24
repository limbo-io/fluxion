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

package io.fluxion.server.infrastructure.schedule.task;

import io.fluxion.server.infrastructure.schedule.BasicCalculation;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;

import java.time.LocalDateTime;
import java.util.function.Consumer;

/**
 * @author Devil
 */
public class DelayTaskFactory {

    public static DelayTask create(String id, LocalDateTime lastTriggerAt, LocalDateTime lastFeedbackAt,
                                   ScheduleOption scheduleOption, Consumer<DelayTask> consumer) {
        BasicCalculation calculation = new BasicCalculation(
            lastTriggerAt, lastFeedbackAt, scheduleOption
        );
        return new DelayTask(id, calculation.triggerAt(), consumer);
    }

    public static DelayTask create(String id, LocalDateTime triggerAt, Consumer<DelayTask> consumer) {
        return new DelayTask(id, triggerAt, consumer);
    }

}
