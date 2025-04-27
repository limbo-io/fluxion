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

import io.fluxion.common.thread.CommonThreadPool;
import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.server.core.schedule.ScheduleDelay;
import io.fluxion.server.core.schedule.cmd.ScheduleDelayDeleteByIdsCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleDelaysLoadCmd;
import io.fluxion.server.core.schedule.query.ScheduleDelayNextCleanQuery;
import io.fluxion.server.core.schedule.query.ScheduleDelayNextTriggerQuery;
import io.fluxion.server.infrastructure.concurrent.LoggingTask;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import org.apache.commons.collections4.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 数据清理 -- 物理删除超过7天的数据
 *
 * @author Devil
 */
public class DataCleaner extends CoreTask {

    private static final int INTERVAL = 7;
    private static final TimeUnit UNIT = TimeUnit.DAYS;

    public DataCleaner() {
        super(0, INTERVAL, UNIT);
    }

    @Override
    public void run() {
        LocalDateTime endAt = TimeUtils.currentLocalDateTime().plusDays(-INTERVAL);
        // schedule_delay
        CommonThreadPool.IO.submit(new LoggingTask(() -> {
            String lastId = "";
            List<ScheduleDelay> delays = Query.query(new ScheduleDelayNextCleanQuery(100, lastId, endAt)).getDelays();
            while (CollectionUtils.isNotEmpty(delays)) {
                Cmd.send(new ScheduleDelayDeleteByIdsCmd(delays.stream().map(ScheduleDelay::getId).collect(Collectors.toList())));
                // 拉取后续的
                lastId = delays.get(delays.size() - 1).getDelayId();
                delays = Query.query(new ScheduleDelayNextCleanQuery(100, lastId, endAt)).getDelays();
            }
        }));
        // broker
        // worker
        // lock
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.FIXED_DELAY;
    }
}
