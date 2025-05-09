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
import io.fluxion.remote.core.constants.JobStateEvent;
import io.fluxion.server.core.job.TaskMonitor;
import io.fluxion.server.infrastructure.cqrs.ICmd;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * @author Devil
 */
@Getter
@AllArgsConstructor
@Builder
public class JobStateTransitionCmd implements ICmd<JobStateTransitionCmd.Response> {

    private String jobId;

    private Node workerNode;

    private LocalDateTime reportAt;

    private TaskMonitor monitor;

    private JobStateEvent event;

    private String result;

    private String errorMsg;

    @Getter
    @AllArgsConstructor
    public static class Response {
        /**
         * 是否成功
         */
        private boolean success;
    }

}
