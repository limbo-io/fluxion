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

import io.limbo.cqrs.core.commandhandling.ICommand;
import lombok.Getter;

import java.time.Instant;

/**
 * 首批 Job 因激活截止时间被拒后，将 Execution 由 RUNNING 收敛为 EXPIRED。
 * 仅用于首批 Job 的 Deadline 拒绝；已运行 Execution 的下游 Job 不受影响（见 CONTEXT.md「Execution 激活截止时间」）。
 *
 * @author Devil
 */
@Getter
public class ExecutionExpiredCmd implements ICommand<Boolean> {

    private final String executionId;

    private final Instant endAt;

    public ExecutionExpiredCmd(String executionId, Instant endAt) {
        this.executionId = executionId;
        this.endAt = endAt;
    }
}