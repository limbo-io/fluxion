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

package io.fluxion.platform.schedule.task;

import io.fluxion.platform.schedule.Executable;

/**
 * @author Brozen
 * @since 2022-10-11
 */
public abstract class AbstractTask implements Executable {

    private final String id;

    /**
     * 是否停止
     */
    private volatile boolean stopped = false;

    public AbstractTask(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public void stop() {
        stopped = true;
    }

    @Override
    public boolean stopped() {
        return stopped;
    }

}
