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

import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.server.infrastructure.schedule.task.AbstractTask;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * task的调度器
 *
 * @author Brozen
 * @since 2022-10-11
 */
@Slf4j
public abstract class TaskScheduler<T extends AbstractTask> implements Scheduler<T> {

    private final Timer timer;

    /**
     * 相同ID的task只会存在一个
     */
    private final Map<String, T> scheduling;

    private final TimeUnit SCHEDULE_UNIT = TimeUnit.MILLISECONDS;

    public TaskScheduler(Timer timer) {
        this.timer = timer;
        this.scheduling = new ConcurrentHashMap<>();
    }

    /**
     * 基于 taskId 如果已经存在则不会重新调度
     *
     * @param task 待执行的对象
     */
    @Override
    public void schedule(T task) {
        String scheduleId = task.id();
        try {
            // 存在就不需要重新放入
            if (scheduling.putIfAbsent(scheduleId, task) != null) {
                return;
            }
            doSchedule(task);
        } catch (Exception e) {
            log.error("Meta task [{}] execute failed", scheduleId, e);
        }
    }

    /**
     * 计算调度延时
     */
    private long calDelay(T task) {
        long delay = Duration.between(TimeUtils.currentLocalDateTime(), task.triggerAt()).toMillis();
        return delay < 0 ? 0 : delay;
    }

    /**
     * 调度
     */
    protected void doSchedule(T task) {
        TaskScheduler<T> scheduler = this;
        timer.schedule(() -> {
            // 已经取消调度了，则不再重新调度作业
            if (task.stopped()) {
                scheduling.remove(task.id());
                return;
            }
            Throwable thrown = null;
            try {
                scheduler.run(task);
            } catch (Throwable e) {
                log.error("[{}] schedule fail id:{}", scheduler.getClass().getSimpleName(), task.id(), e);
                thrown = e;
            } finally {
                afterExecute(task, thrown);
            }
        }, calDelay(task), SCHEDULE_UNIT);
    }

    @Override
    public void stop(String id) {
        T task = scheduling.remove(id);
        if (task != null) {
            task.stop();
        }
    }

    protected abstract Runnable run(T task);

    protected abstract void afterExecute(T task, Throwable thrown);

}
