/*
 * Copyright 2025-2030 limbo-io Team (https://github.com/limbo-io).
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

package io.fluxion.server.infrastructure.concurrent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoggingTask implements Runnable {

    private final Runnable task;

    public LoggingTask(Runnable task) {
        this.task = task;
    }

    @Override
    public void run() {
        try {
            task.run();
        } catch (Throwable t) {
            log.error("[{}] run error", task.getClass().getSimpleName(), t);
        }
    }
}