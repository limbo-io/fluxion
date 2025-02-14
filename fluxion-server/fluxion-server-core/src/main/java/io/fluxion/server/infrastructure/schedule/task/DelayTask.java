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

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.function.Consumer;

/**
 * 延迟一定时间的任务，执行一次
 *
 * @author Devil
 * @since 2022/12/19
 */
@Slf4j
public class DelayTask extends AbstractTask {

    private final LocalDateTime triggerAt;

    /**
     * 业务逻辑
     */
    private final Consumer<DelayTask> consumer;

    public DelayTask(String id, LocalDateTime triggerAt, Consumer<DelayTask> consumer) {
        super(id);
        this.triggerAt = triggerAt;
        this.consumer = consumer;
    }

    @Override
    public void run() {
        consumer.accept(this);
    }

    @Override
    public LocalDateTime triggerAt() {
        return triggerAt;
    }

}
