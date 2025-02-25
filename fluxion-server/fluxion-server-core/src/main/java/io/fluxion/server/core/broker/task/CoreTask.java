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

package io.fluxion.server.core.broker.task;

import lombok.Getter;

import java.util.concurrent.TimeUnit;

/**
 * @author Devil
 */
@Getter
public abstract class CoreTask implements Runnable {

    /**
     * 间隔
     */
    protected final int interval;

    /**
     * 单位
     */
    protected final TimeUnit unit;

    public CoreTask(int interval, TimeUnit unit) {
        this.interval = interval;
        this.unit = unit;
    }

}
