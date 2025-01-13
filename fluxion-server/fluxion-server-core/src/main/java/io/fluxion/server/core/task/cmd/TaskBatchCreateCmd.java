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

package io.fluxion.server.core.task.cmd;

import io.fluxion.server.core.task.TaskRefType;
import io.fluxion.server.infrastructure.cqrs.ICmd;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * @author Devil
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskBatchCreateCmd implements ICmd<TaskBatchCreateCmd.Response> {
    /**
     * 执行记录ID
     */
    private String executionId;

    /**
     * 计划触发时间
     */
    private LocalDateTime triggerAt;

    private TaskRefType refType;

    private List<String> refIds;

    @Getter
    @AllArgsConstructor
    public static class Response {
        /**
         * refId - taskId
         */
        private Map<String, String> refTaskIds;
    }

}
