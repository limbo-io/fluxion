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

import io.fluxion.server.core.broker.cmd.BucketRebalanceCmd;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.schedule.ScheduleType;

import java.util.concurrent.TimeUnit;

/**
 * bucket对应的broker无效的，重新进行数据绑定
 *
 * @author Devil
 */
public class BucketChecker extends CoreTask {

    private static final int INTERVAL = 30;
    private static final TimeUnit UNIT = TimeUnit.DAYS;

    public BucketChecker() {
        super(0, INTERVAL, UNIT);
    }

    @Override
    public void run() {
        Cmd.send(new BucketRebalanceCmd());
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.FIXED_DELAY;
    }
}
