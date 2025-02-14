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

package io.fluxion.server.infrastructure.schedule.scheduler;

import io.fluxion.common.thread.NamedThreadFactory;
import io.netty.util.HashedWheelTimer;

import java.util.concurrent.TimeUnit;

/**
 * @author Devil
 */
public class TimingWheelTimer implements Timer {

    /**
     * 依赖netty的时间轮算法进行作业调度
     */
    private final io.netty.util.Timer timer;

    /**
     * 使用指定执行器构造一个调度器，该调度器基于哈希时间轮算法。
     *
     * @param tickDuration 轮询间隔
     * @param unit         时间单位
     */
    public TimingWheelTimer(long tickDuration, TimeUnit unit) {
        this.timer = new HashedWheelTimer(NamedThreadFactory.newInstance(this.getClass().getSimpleName()), tickDuration, unit);
    }

    @Override
    public void schedule(Runnable runnable, long delay, TimeUnit unit) {
        // 在timer上调度作业执行
        this.timer.newTimeout(timeout -> {
            runnable.run();
        }, delay, unit);
    }
}
