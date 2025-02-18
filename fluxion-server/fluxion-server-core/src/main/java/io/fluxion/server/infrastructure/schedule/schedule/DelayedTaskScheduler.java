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

import io.fluxion.server.infrastructure.schedule.scheduler.TaskScheduler;
import io.fluxion.server.infrastructure.schedule.task.DelayedTask;
import lombok.extern.slf4j.Slf4j;

/**
 * 延迟执行一次
 *
 * @author Brozen
 * @since 2022-10-11
 */
@Slf4j
public class DelayedTaskScheduler extends TaskScheduler<DelayedTask> {

    public DelayedTaskScheduler(Timer timer) {
        super(timer);
    }

    @Override
    protected Runnable run(DelayedTask task) {
        return task::run;
    }

    @Override
    protected void afterExecute(DelayedTask task, Throwable thrown) {
        // 执行后移除 之后相同ID的可以再次执行
        stop(task.id());
    }
}
