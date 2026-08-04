/*
 * Copyright 2025-2030 Fluxion Team (https://github.com/Fluxion-io).
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

package io.fluxion.server.core.job.cmd;

import io.fluxion.server.core.job.JobMonitor;
import io.limbo.cqrs.core.command.ICommand;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * @author Devil
 */
@Getter
public class JobSuccessCmd implements ICommand<Boolean> {

    private final String jobId;

    private final LocalDateTime reportAt;

    private final Integer dispatchAttempt;

    private final String workerAddress;

    private JobMonitor monitor;

    private String result;

    public JobSuccessCmd(String jobId, LocalDateTime reportAt, JobMonitor monitor, String result) {
        this(jobId, reportAt, null, null, monitor, result);
    }

    public JobSuccessCmd(String jobId, LocalDateTime reportAt, Integer dispatchAttempt, JobMonitor monitor, String result) {
        this(jobId, reportAt, dispatchAttempt, null, monitor, result);
    }

    public JobSuccessCmd(String jobId, LocalDateTime reportAt, Integer dispatchAttempt, String workerAddress, JobMonitor monitor, String result) {
        this.jobId = jobId;
        this.reportAt = reportAt;
        this.dispatchAttempt = dispatchAttempt;
        this.workerAddress = workerAddress;
        this.monitor = monitor;
        this.result = result;
    }
}
