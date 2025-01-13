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

import java.time.LocalDateTime;

/**
 * @author Brozen
 * @since 2022-10-11
 */
public abstract class AbstractTask {

    private final String id;

    /**
     * 是否停止
     */
    private volatile boolean stopped = false;

    public AbstractTask(String id) {
        this.id = id;
    }

    /**
     * 任务执行
     */
    public abstract void run();

    /**
     * 任务应该被执行的时间
     */
    public abstract LocalDateTime triggerAt();

    public String id() {
        return id;
    }

    public void stop() {
        stopped = true;
    }

    public boolean stopped() {
        return stopped;
    }

}
