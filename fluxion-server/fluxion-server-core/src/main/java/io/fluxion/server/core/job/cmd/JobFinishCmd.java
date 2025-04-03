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

import io.fluxion.server.core.job.JobStatus;
import io.fluxion.server.infrastructure.cqrs.ICmd;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * @author Devil
 */
@Getter
public class JobFinishCmd implements ICmd<Boolean> {

    private final String jobId;

    private final LocalDateTime reportAt;

    private final JobStatus oldStatus;

    private final JobStatus newStatus;

    /**
     * 执行失败时候返回的信息
     */
    private String errorMsg;

    public JobFinishCmd(String jobId, LocalDateTime reportAt, JobStatus oldStatus, JobStatus newStatus) {
        this.jobId = jobId;
        this.reportAt = reportAt;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }

    public JobFinishCmd(String jobId, LocalDateTime reportAt, JobStatus oldStatus, JobStatus newStatus, String errorMsg) {
        this.jobId = jobId;
        this.reportAt = reportAt;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.errorMsg = errorMsg;
    }
}
