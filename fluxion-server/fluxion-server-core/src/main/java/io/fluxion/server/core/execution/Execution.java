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

package io.fluxion.server.core.execution;

import io.fluxion.server.core.context.RunContext;
import lombok.Getter;

/**
 * 执行记录
 *
 * @author Devil
 */
@Getter
public class Execution {

    private final String executionId;

    private final Executable executable;

    private final ExecutionStatus status;

    public Execution(String executionId, Executable executable, ExecutionStatus status) {
        this.executionId = executionId;
        this.executable = executable;
        this.status = status;
    }

    public void execute() {
        RunContext runContext = RunContext.of(executionId);
        executable.execute(runContext);
    }

}
