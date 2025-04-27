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

import io.fluxion.server.core.schedule.ScheduleDelayConstants;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import lombok.extern.slf4j.Slf4j;

/**
 * 检查异常状态job并进行恢复
 *
 * todo @d later
 * 1. 加载 create 状态执行TaskRunCmd
 * 2. 加载 dispatched + running 状态 长时间没有心跳 修改为最终态
 * 3. 判断超时
 *
 * @author Devil
 */
@Slf4j
public class JobChecker extends CoreTask {

    public JobChecker() {
        super(0, ScheduleDelayConstants.LOAD_INTERVAL, ScheduleDelayConstants.LOAD_TIME_UNIT);
    }

    @Override
    public void run() {
        // job create 还没执行 JobRunCmd broker宕机导致还是create状态
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.FIXED_DELAY;
    }
}
