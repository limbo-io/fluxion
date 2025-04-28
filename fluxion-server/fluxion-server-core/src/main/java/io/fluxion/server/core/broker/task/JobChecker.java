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
import io.fluxion.remote.core.constants.BrokerRemoteConstant;
import io.fluxion.server.core.job.Job;
import io.fluxion.server.core.job.cmd.JobResetCmd;
import io.fluxion.server.core.job.cmd.JobRunCmd;
import io.fluxion.server.core.job.query.JobByIdQuery;
import io.fluxion.server.core.job.query.JobInitBlockedQuery;
import io.fluxion.server.core.job.query.JobUnReportQuery;
import io.fluxion.server.infrastructure.concurrent.LoggingTask;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 检查异常状态job并进行恢复
 *
 * @author Devil
 */
@Slf4j
public class JobChecker extends CoreTask {

    private static final int INTERVAL = 1;
    private static final TimeUnit UNIT = TimeUnit.MINUTES;

    public JobChecker() {
        super(0, INTERVAL, UNIT);
    }

    @Override
    public void run() {
        // job 创建了(inited) 还没执行 JobRunCmd broker宕机导致还是create状态 重新run
        CommonThreadPool.IO.submit(new LoggingTask(() -> {
            LocalDateTime endAt = TimeUtils.currentLocalDateTime().plusMinutes(- INTERVAL);
            String lastId = "";
            // 返回的job必定是inited
            List<String> jobIds = Query.query(new JobInitBlockedQuery(100, lastId, endAt)).getJobIds();
            while (CollectionUtils.isNotEmpty(jobIds)) {
                for (String jobId : jobIds) {
                    CommonThreadPool.IO.submit(new LoggingTask(() -> {
                        // 初始化状态并运行
                        Cmd.send(new JobResetCmd(jobId));
                        Job job = Query.query(new JobByIdQuery(jobId)).getJob();
                        Cmd.send(new JobRunCmd(job));
                    }));
                }
                // 拉取后续的
                lastId = jobIds.get(jobIds.size() - 1);
                jobIds = Query.query(new JobInitBlockedQuery(100, lastId, endAt)).getJobIds();
            }
        }));

        // running 但是report已经超过一定时间没有上报
        CommonThreadPool.IO.submit(new LoggingTask(() -> {
            LocalDateTime endAt = TimeUtils.currentLocalDateTime().plusSeconds(- 2 * BrokerRemoteConstant.JOB_REPORT_SECONDS);
            String lastId = "";
            // 返回的job必定是inited
            List<String> jobIds = Query.query(new JobUnReportQuery(100, lastId, endAt)).getJobIds();
            while (CollectionUtils.isNotEmpty(jobIds)) {
                for (String jobId : jobIds) {
                    CommonThreadPool.IO.submit(new LoggingTask(() -> {
                        // 初始化状态并运行
                        Cmd.send(new JobResetCmd(jobId));
                        Job job = Query.query(new JobByIdQuery(jobId)).getJob();
                        Cmd.send(new JobRunCmd(job));
                    }));
                }
                // 拉取后续的
                lastId = jobIds.get(jobIds.size() - 1);
                jobIds = Query.query(new JobUnReportQuery(100, lastId, endAt)).getJobIds();
            }
        }));

        // running状态 执行超过配置的超时时间 todo
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.FIXED_DELAY;
    }
}
