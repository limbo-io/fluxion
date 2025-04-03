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

package io.fluxion.server.core.execution.cmd;

import io.fluxion.remote.core.constants.ExecuteResult;
import io.fluxion.server.infrastructure.cqrs.ICmd;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * @author Devil
 */
@Getter
public class ExecutableFeedbackCmd implements ICmd<Boolean> {

    private final String jobId;

    private final LocalDateTime reportAt;

    private final ExecuteResult executeResult;

    private String errorMsg;

    public ExecutableFeedbackCmd(String jobId, LocalDateTime reportAt, ExecuteResult executeResult) {
        this.jobId = jobId;
        this.reportAt = reportAt;
        this.executeResult = executeResult;
    }

    public ExecutableFeedbackCmd(String jobId, LocalDateTime reportAt, ExecuteResult executeResult, String errorMsg) {
        this.jobId = jobId;
        this.reportAt = reportAt;
        this.executeResult = executeResult;
        this.errorMsg = errorMsg;
    }
}
