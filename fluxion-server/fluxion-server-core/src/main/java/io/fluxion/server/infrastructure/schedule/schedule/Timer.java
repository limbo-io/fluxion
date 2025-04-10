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

package io.fluxion.server.infrastructure.schedule.schedule;

import java.util.concurrent.TimeUnit;

public interface Timer {
    /**
     * 在一定时间后执行任务
     *
     * @param runnable 运行内容
     * @param delay    延迟
     * @param unit     时间单位
     */
    void schedule(Runnable runnable, long delay, TimeUnit unit);

}
