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

package io.fluxion.server.core.schedule;

import java.util.concurrent.TimeUnit;

/**
 * @author Devil
 */
public interface ScheduleConstants {

    int LOAD_INTERVAL = 10;

    TimeUnit LOAD_TIME_UNIT = TimeUnit.MINUTES;

    long LOAD_INTERVAL_SECONDS = LOAD_TIME_UNIT.toSeconds(LOAD_INTERVAL);

}
