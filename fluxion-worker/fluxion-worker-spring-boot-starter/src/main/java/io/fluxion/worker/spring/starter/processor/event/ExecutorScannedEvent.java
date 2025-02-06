/*
 * Copyright 2020-2024 Limbo Team (https://github.com/limbo-world).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxion.worker.spring.starter.processor.event;

import io.fluxion.worker.core.executor.Executor;
import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * @author Brozen
 * @since 2022-10-24
 */
public class ExecutorScannedEvent extends ApplicationEvent {

    private final List<Executor> executors;

    /**
     * 生成 Executor 扫描完成事件
     */
    public ExecutorScannedEvent(List<Executor> executors) {
        super(executors);
        this.executors = executors;
    }

    public List<Executor> getExecutors() {
        return executors;
    }
}
