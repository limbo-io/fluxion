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

import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.schedule.Schedule;
import io.fluxion.server.core.schedule.ScheduleConstants;
import io.fluxion.server.core.schedule.cmd.ScheduleTriggerCmd;
import io.fluxion.server.core.schedule.query.ScheduleNextTriggerQuery;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 加载 ScheduledTask 并执行
 *
 * @author Devil
 */
@Slf4j
public class ScheduleLoadTask extends CoreTask {

    public ScheduleLoadTask() {
        super(ScheduleConstants.LOAD_INTERVAL, ScheduleConstants.LOAD_TIME_UNIT);
    }

    @Override
    public void run() {
        String brokerId = BrokerContext.broker().id();
        try {
            List<Schedule> schedules = Query.query(new ScheduleNextTriggerQuery(brokerId, 100)).getSchedules();
            while (CollectionUtils.isNotEmpty(schedules)) {
                for (Schedule schedule : schedules) {
                    Cmd.send(new ScheduleTriggerCmd(schedule));
                }
                // 拉取后续的
                schedules = Query.query(new ScheduleNextTriggerQuery(brokerId, 100)).getSchedules();
            }
        } catch (Exception e) {
            log.error("[{}] execute fail", this.getClass().getSimpleName(), e);
        }
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.FIXED_RATE;
    }
}
