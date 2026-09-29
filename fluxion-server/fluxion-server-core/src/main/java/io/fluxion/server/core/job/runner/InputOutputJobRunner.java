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

package io.fluxion.server.core.job.runner;

import io.fluxion.remote.core.constants.JobStateEvent;
import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.job.Job;
import io.fluxion.server.core.job.JobType;
import io.fluxion.server.core.job.cmd.JobExpiredCmd;
import io.fluxion.server.core.job.cmd.JobFailCmd;
import io.fluxion.server.core.job.cmd.JobStateTransitionCmd;
import io.fluxion.server.core.job.cmd.JobSuccessCmd;
import io.limbo.cqrs.core.commandhandling.Cmd;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * @author Devil
 */
@Slf4j
@Component
public class InputOutputJobRunner extends JobRunner {

    @Override
    public JobType type() {
        return JobType.INPUT_OUTPUT;
    }

    @Override
    public void run(Job job) {
        // 工作流根节点（首批 Job）的激活截止时间最终检查：
        // 本地执行不经 Worker RPC，在此拒绝迟到启动；与下发 RPC 固定同一 UTC 口径（见 CONTEXT.md「统一时间模型」）。
        // ponytail: 排程域仍本机墙钟，非 UTC 统一时区部署下激活窗口随 offset 后移；Instant 迁移落地后归一。
        if (job.getDeadlineAt() != null && !job.getDeadlineAt().isAfter(Instant.now())) {
            log.warn("[InputOutputTaskRunner] job {} start deadline exceeded", job.getJobId());
            Cmd.send(new JobExpiredCmd(job.getJobId(), null, Instant.now()));
            return;
        }
        JobStateTransitionCmd.Response response = Cmd.send(new JobStateTransitionCmd(
            job.getJobId(),
            null,
            BrokerContext.broker().node(),
            Instant.now(),
            null,
            JobStateEvent.START,
            null, null
        ));
        if (!response.isSuccess()) {
            log.warn("JobStart fail jobId:{}", job.getJobId());
            return;
        }
        try {
            if (log.isDebugEnabled()) {
                log.debug("InputOutputTaskRunner taskId:{}", job.getJobId());
            }
            Cmd.send(new JobSuccessCmd(
                job.getJobId(),
                Instant.now(),
                null,
                ""
            ));
        } catch (Exception e) {
            log.error("InputOutputTaskRunner error", e);
            Cmd.send(new JobFailCmd(
                job.getJobId(),
                Instant.now(),
                e.getMessage(),
                null
            ));
        }
    }
}
