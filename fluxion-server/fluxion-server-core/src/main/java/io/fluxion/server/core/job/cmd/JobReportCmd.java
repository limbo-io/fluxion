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

import io.fluxion.remote.core.cluster.Node;
import io.fluxion.remote.core.constants.JobStatus;
import io.fluxion.server.core.job.JobMonitor;
import io.fluxion.server.infrastructure.cqrs.ICmd;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * @author Devil
 */
@Getter
@AllArgsConstructor
@Builder
public class JobReportCmd implements ICmd<JobReportCmd.Response> {

    private String jobId;

    private Node workerNode;

    private LocalDateTime reportAt;

    private JobMonitor monitor;

    private JobStatus status;

    @Getter
    @AllArgsConstructor
    public static class Response {
        /**
         * 是否成功
         */
        private boolean success;
    }

}
