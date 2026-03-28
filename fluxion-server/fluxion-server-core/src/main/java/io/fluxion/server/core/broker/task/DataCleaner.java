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
import io.fluxion.server.core.schedule.ScheduleDelay;
import io.fluxion.server.core.schedule.cmd.ScheduleDelayDeleteByIdsCmd;
import io.fluxion.server.core.schedule.query.ScheduleDelayNextCleanQuery;
import io.fluxion.server.infrastructure.concurrent.LoggingTask;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import io.limbo.cqrs.spring.command.Cmd;
import io.limbo.cqrs.spring.query.Query;
import io.limbo.utils.time.TimeUtils;
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
            String lastDelayId = "";
            ScheduleDelayNextCleanQuery.Response response = Query.query(ScheduleDelayNextCleanQuery.builder()
                .limit(100).lastDelayId(lastDelayId).endAt(endAt).build());
            List<ScheduleDelay> delays = response.getDelays();
            while (CollectionUtils.isNotEmpty(delays)) {
                List<ScheduleDelay.ID> ids = delays.stream().map(ScheduleDelay::getId).collect(Collectors.toList());
                Cmd.send(ScheduleDelayDeleteByIdsCmd.builder().ids(ids).build());
                // 拉取后续的
                lastDelayId = delays.get(delays.size() - 1).getDelayId();
                response = Query.query(ScheduleDelayNextCleanQuery.builder()
                    .limit(100).lastDelayId(lastDelayId).endAt(endAt).build());
                delays = response.getDelays();
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
