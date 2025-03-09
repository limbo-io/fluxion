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

import io.fluxion.server.infrastructure.schedule.ScheduleType;

import java.util.concurrent.TimeUnit;

/**
 * @author Devil
 */
public class ScheduleDelayLoadTask extends CoreTask {

    public static final int LOAD_INTERVAL = 1;

    public static final TimeUnit LOAD_TIME_UNIT = TimeUnit.MINUTES;

    public ScheduleDelayLoadTask() {
        super(LOAD_INTERVAL, LOAD_TIME_UNIT);
    }

    @Override
    public void run() {

    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.FIXED_DELAY;
    }
}
