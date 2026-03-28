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

import io.limbo.utils.time.Formatters;
import io.limbo.utils.time.LocalDateTimeUtils;
import io.limbo.utils.time.TimeUtils;
import io.fluxion.remote.core.constants.WorkerRemoteConstant;
import io.fluxion.server.core.execution.fault.FaultToleranceCoordinator;
import io.fluxion.server.core.worker.cmd.WorkerSliceOfflineCmd;
import io.limbo.cqrs.spring.command.Cmd;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

    @Resource
    private FaultToleranceCoordinator faultToleranceCoordinator;

    public WorkerChecker() {
        super(0, WorkerRemoteConstant.HEARTBEAT_TIMEOUT_SECOND, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        try {
            LocalDateTime endTime = TimeUtils.currentLocalDateTime().plusSeconds(-WorkerRemoteConstant.HEARTBEAT_TIMEOUT_SECOND * 2);
            List<String> offlineWorkerIds = new ArrayList<>();

            WorkerSliceOfflineCmd.Response response = Cmd.send(new WorkerSliceOfflineCmd(lastCheckAt, endTime, limit));
            long num = response.getNum();

            // 获取下线的 Worker ID 列表用于故障迁移
            if (response.getWorkerIds() != null) {
                offlineWorkerIds.addAll(response.getWorkerIds());
            }

            while (num >= limit) {
                response = Cmd.send(new WorkerSliceOfflineCmd(lastCheckAt, endTime, limit));
                num = response.getNum();
                if (response.getWorkerIds() != null) {
                    offlineWorkerIds.addAll(response.getWorkerIds());
                }
            }
            lastCheckAt = endTime;

            // 通知容错协调器 Worker 下线
            for (String workerId : offlineWorkerIds) {
                faultToleranceCoordinator.onWorkerOffline(workerId);
            }
        } catch (Exception e) {
            log.error("[{}] execute fail", this.getClass().getSimpleName(), e);
        }
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.FIXED_DELAY;
    }
}
