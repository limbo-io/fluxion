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

import io.limbo.utils.time.TimeUtils;
import io.fluxion.server.core.job.cmd.JobFailCmd;
import io.fluxion.server.core.job.query.JobRunningByWorkerQuery;
import io.fluxion.server.core.worker.WorkerLivenessProperties;
import io.fluxion.server.core.worker.cmd.WorkerSliceOfflineCmd;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.limbo.cqrs.core.commandhandling.Cmd;
import io.limbo.cqrs.core.queryhandling.Query;

/**
* 检查worker是否下线
 *
 * @author Devil
 */
@Slf4j
public class WorkerChecker extends CoreTask {

    private static final int limit = 100;

    private final WorkerLivenessProperties livenessProperties;

    public WorkerChecker(WorkerLivenessProperties livenessProperties) {
        super(0, livenessProperties.getCheckIntervalSeconds(), TimeUnit.SECONDS);
        this.livenessProperties = livenessProperties;
    }

    @Override
    public void run() {
        try {
            LocalDateTime cutoff = TimeUtils.currentLocalDateTime()
                .plusSeconds(-livenessProperties.getOfflineTimeoutSeconds());
            LocalDateTime cursorHeartbeatAt = null;
            String cursorWorkerId = null;
            do {
                WorkerSliceOfflineCmd.Response response = Cmd.send(new WorkerSliceOfflineCmd(
                    cutoff, cursorHeartbeatAt, cursorWorkerId, limit
                ));
                // 按持久化 Job 状态接管，不能依赖本 Broker 的 execution 内存索引。
                for (String workerId : response.getWorkerIds()) {
                    List<JobRunningByWorkerQuery.JobRunning> jobs;
                    String lastJobId = null;
                    do {
                        jobs = Query.query(new JobRunningByWorkerQuery(workerId, lastJobId, limit)).getJobs();
                        for (JobRunningByWorkerQuery.JobRunning job : jobs) {
                            Cmd.send(new JobFailCmd(
                                job.getJobId(), TimeUtils.currentLocalDateTime(), job.getDispatchAttempt(),
                                "Worker offline: " + workerId, null
                            ));
                        }
                        if (!jobs.isEmpty()) {
                            lastJobId = jobs.get(jobs.size() - 1).getJobId();
                        }
                    } while (jobs.size() >= limit);
                }
                cursorHeartbeatAt = response.getNextCursorHeartbeatAt();
                cursorWorkerId = response.getNextCursorWorkerId();
            } while (cursorHeartbeatAt != null);
        } catch (Exception e) {
            log.error("[{}] execute fail", this.getClass().getSimpleName(), e);
        }
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.FIXED_DELAY;
    }
}
