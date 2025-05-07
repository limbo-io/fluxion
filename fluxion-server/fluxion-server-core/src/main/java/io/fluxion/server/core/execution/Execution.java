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

import io.fluxion.server.core.execution.query.ExecutableByIdQuery;
import io.fluxion.server.infrastructure.cqrs.Query;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * 执行记录
 *
 * @author Devil
 */
@Getter
public class Execution {

    private final String id;

    private final ExecutionStatus status;

    private final String executableId;

    private final String executableVersion;

    private final ExecutableType type;

    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private Executable executable;

    public Execution(String id, ExecutionStatus status, String executableId, String executableVersion, ExecutableType type) {
        this.id = id;
        this.type = type;
        this.status = status;
        this.executableId = executableId;
        this.executableVersion = executableVersion;
    }

    public Executable executable() {
        if (executable == null) {
            executable = Query.query(new ExecutableByIdQuery(executableId, type, executableVersion)).getExecutable();
        }
        return executable;
    }

}
