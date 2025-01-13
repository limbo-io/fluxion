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
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import io.fluxion.common.thread.NamedThreadFactory;
import io.fluxion.common.utils.time.TimeUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 基于Netty时间轮算法的作业执行器。一个作业申请执行后，会计算下次执行的间隔，并注册到时间轮上。
 * 当时间轮触发作业执行时，将进入作业下发流程，并将生成的实例分发给下游。
 *
 * @author Brozen
 * @since 2021-05-18
 */
@Slf4j
public abstract class HashedWheelTimerScheduler<T extends AbstractTask> implements Scheduler<T> {

    /**
     * 依赖netty的时间轮算法进行作业调度
     */
    private final Timer timer;

    /**
     * 使用指定执行器构造一个调度器，该调度器基于哈希时间轮算法。
     */
    protected HashedWheelTimerScheduler(long tickDuration, TimeUnit unit) {
        this.timer = new HashedWheelTimer(NamedThreadFactory.newInstance(this.getClass().getSimpleName()), tickDuration, unit);
    }

    /**
     * 计算延迟时间 并调度
     */
    protected void calAndSchedule(T scheduled, Consumer<T> consumer) {
        long delay = Duration.between(TimeUtils.currentLocalDateTime(), scheduled.triggerAt()).toMillis();
        delay = delay < 0 ? 0 : delay;

        // 在timer上调度作业执行
        this.timer.newTimeout(timeout -> {
            // 已经取消调度了，则不再重新调度作业
            if (scheduled.stopped()) {
                return;
            }
            consumer.accept(scheduled);
        }, delay, TimeUnit.MILLISECONDS);
    }

}
