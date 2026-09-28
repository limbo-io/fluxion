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
import io.limbo.cqrs.core.queryhandling.Query;
import java.time.LocalDateTime;
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

    /**
     * Worker ID executing this execution
     */
    private String workerId;

    /**
     * Number of dispatch attempts
     */
    private Integer dispatchAttempt;

    /**
     * Last state update timestamp
     */
    private LocalDateTime stateUpdatedAt;

    /**
     * Broker ID that owns the lease
     */
    private String leaseOwner;

    /**
     * Lease expiration time
     */
    private LocalDateTime leaseUntil;

    /**
     * 激活截止时间快照：仅调度型 Execution 的首批 Job 创建时从 Execution.deadlineAt 复制；为空表示允许延迟启动。
     */
    private LocalDateTime deadlineAt;

    public Execution(String id, ExecutionStatus status, String executableId, String executableVersion, ExecutableType type,
                     LocalDateTime deadlineAt) {
        this.id = id;
        this.type = type;
        this.status = status;
        this.executableId = executableId;
        this.executableVersion = executableVersion;
        this.deadlineAt = deadlineAt;
    }

    public Executable executable() {
        if (executable == null) {
            executable = Query.query(new ExecutableByIdQuery(executableId, type, executableVersion)).getExecutable();
        }
        return executable;
    }

}
