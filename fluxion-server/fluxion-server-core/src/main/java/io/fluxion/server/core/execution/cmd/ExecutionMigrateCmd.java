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

import io.limbo.cqrs.core.command.ICommand;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 执行迁移命令
 * 用于故障迁移场景，将执行任务从故障 Worker 迁移到其他可用 Worker
 */
@Getter
@AllArgsConstructor
public class ExecutionMigrateCmd implements ICommand<Void> {

    /**
     * 执行ID
     */
    private String executionId;

    /**
     * 原Worker ID (故障Worker)
     */
    private String sourceWorkerId;
}