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

import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.server.core.execution.cmd.ExecutableFailCmd;
import io.fluxion.server.core.execution.cmd.ExecutableSuccessCmd;
import io.fluxion.server.core.job.InputOutputJob;
import io.fluxion.server.core.job.Job;
import io.fluxion.server.core.job.JobType;
import io.fluxion.server.core.job.cmd.JobStartCmd;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
        InputOutputJob inputOutputTask = (InputOutputJob) job;
        Boolean started = Cmd.send(new JobStartCmd(inputOutputTask.getJobId(), TimeUtils.currentLocalDateTime()));
        if (!started) {
            return;
        }
        try {
            if (log.isDebugEnabled()) {
                log.debug("InputOutputTaskRunner taskId:{}", job.getJobId());
            }
            Cmd.send(new ExecutableSuccessCmd(
                job.getJobId(),
                TimeUtils.currentLocalDateTime(),
                null
            ));
        } catch (Exception e) {
            log.error("InputOutputTaskRunner error", e);
            Cmd.send(new ExecutableFailCmd(
                job.getJobId(),
                TimeUtils.currentLocalDateTime(),
                e.getMessage(),
                null
            ));
        }
    }
}
