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

import io.fluxion.server.core.execution.cmd.ExecutionCleanCmd;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import io.limbo.utils.time.TimeUtils;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import io.limbo.cqrs.spring.gateway.CommandGateway;
import javax.annotation.Resource;

/**
* 数据清理 -- 物理删除超过7天的数据
 *
 * @author Devil
 */
public class DataCleaner extends CoreTask {
    @Resource
    private CommandGateway commandGateway;

    private static final int INTERVAL = 7;
    private static final TimeUnit UNIT = TimeUnit.DAYS;

    public DataCleaner() {
        super(0, INTERVAL, UNIT);
    }

    @Override
    public void run() {
        LocalDateTime endAt = TimeUtils.currentLocalDateTime().plusDays(-INTERVAL);
        commandGateway.send(new ExecutionCleanCmd(endAt));
        // broker
        // worker
        // lock
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.FIXED_DELAY;
    }
}
