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

import io.fluxion.common.utils.time.Formatters;
import io.fluxion.common.utils.time.LocalDateTimeUtils;
import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.remote.core.constants.WorkerRemoteConstant;
import io.fluxion.server.core.worker.cmd.WorkerSliceOfflineCmd;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 检查worker是否下线
 *
 * @author Devil
 */
@Slf4j
public class WorkerChecker extends CoreTask {

    private static final int limit = 100;

    private LocalDateTime lastCheckAt = LocalDateTimeUtils.parse("2000-01-01 00:00:00", Formatters.YMD_HMS);

    public WorkerChecker() {
        super(0, WorkerRemoteConstant.HEARTBEAT_TIMEOUT_SECOND, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        try {
            LocalDateTime endTime = TimeUtils.currentLocalDateTime().plusSeconds(-WorkerRemoteConstant.HEARTBEAT_TIMEOUT_SECOND * 2);
            long num = Cmd.send(new WorkerSliceOfflineCmd(lastCheckAt, endTime, limit)).getNum();
            while (num >= limit) {
                // 拉取后续的
                num = Cmd.send(new WorkerSliceOfflineCmd(lastCheckAt, endTime, limit)).getNum();
            }
            lastCheckAt = endTime;
        } catch (Exception e) {
            log.error("[{}] execute fail", this.getClass().getSimpleName(), e);
        }

    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.FIXED_DELAY;
    }
}
