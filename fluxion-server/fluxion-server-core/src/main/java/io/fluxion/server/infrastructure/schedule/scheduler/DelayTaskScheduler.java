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

package io.fluxion.server.infrastructure.schedule.scheduler;

import io.fluxion.server.infrastructure.schedule.task.ScheduledTask;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 延迟执行一次
 *
 * @author Brozen
 * @since 2022-10-11
 */
@Slf4j
public class DelayTaskScheduler extends TaskScheduler<ScheduledTask> {

    public DelayTaskScheduler(long tickDuration, TimeUnit unit) {
        super(tickDuration, unit);
    }

    @Override
    protected Consumer<ScheduledTask> consumer() {
        return task -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("[DelayTaskScheduler] schedule fail id:{}", task.id(), e);
                stop(task.id());
            }
        };
    }

}
