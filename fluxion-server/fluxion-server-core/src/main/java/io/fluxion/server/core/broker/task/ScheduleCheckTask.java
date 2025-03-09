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

import io.fluxion.server.core.schedule.Schedule;
import io.fluxion.server.core.schedule.cmd.ScheduleBrokerElectCmd;
import io.fluxion.server.core.schedule.query.ScheduleNotOwnerQuery;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.lock.DistributedLock;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Schedule对应的broker无效的，重新进行数据绑定
 *
 * @author Devil
 * @date 2025/1/9
 */
public class ScheduleCheckTask extends CoreTask {

    private final DistributedLock distributedLock;

    private static final String LOCK = "ScheduleCheckTask";

    public ScheduleCheckTask(int interval, TimeUnit unit, DistributedLock distributedLock) {
        super(interval, unit);
        this.distributedLock = distributedLock;
    }

    @Override
    public void run() {
        if (!distributedLock.lock(LOCK, 5000)) {
            return;
        }
        for (int i = 0; i < 10; i++) {
            if (!distributedLock.lock(LOCK, 5000)) {
                return;
            }
            List<Schedule> schedules = Query.query(new ScheduleNotOwnerQuery(100)).getSchedules();
            if (CollectionUtils.isEmpty(schedules)) {
                distributedLock.unlock(LOCK);
                return;
            }
            for (Schedule schedule : schedules) {
                Cmd.send(new ScheduleBrokerElectCmd(schedule));
            }
        }
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.FIXED_DELAY;
    }
}
