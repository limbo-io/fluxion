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

import io.fluxion.server.infrastructure.schedule.task.AbstractTask;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * task的调度器
 *
 * @author Brozen
 * @since 2022-10-11
 */
@Slf4j
public abstract class TaskScheduler<T extends AbstractTask> extends HashedWheelTimerScheduler<T> {

    private final Map<String, T> scheduling;

    public TaskScheduler(long tickDuration, TimeUnit unit) {
        super(tickDuration, unit);
        this.scheduling = new ConcurrentHashMap<>();
    }

    @Override
    public void schedule(T task) {
        String scheduleId = task.id();
        try {
            // 存在就不需要重新放入
            if (scheduling.putIfAbsent(scheduleId, task) != null) {
                return;
            }

            calAndSchedule(task, consumer());
        } catch (Exception e) {
            log.error("Meta task [{}] execute failed", scheduleId, e);
        }
    }

    @Override
    public void stop(String id) {
        T task = scheduling.remove(id);
        if (task != null) {
            task.stop();
        }
    }

    protected abstract Consumer<T> consumer();

}
