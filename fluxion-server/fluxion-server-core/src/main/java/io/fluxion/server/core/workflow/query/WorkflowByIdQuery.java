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

package io.fluxion.server.core.workflow.query;

import io.fluxion.server.core.workflow.Workflow;
import io.fluxion.server.infrastructure.cqrs.IQuery;
import io.fluxion.server.infrastructure.version.model.VersionMode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author Devil
 */
@Getter
public class WorkflowByIdQuery implements IQuery<WorkflowByIdQuery.Response> {

    private final String id;

    private String version;

    private VersionMode versionMode = VersionMode.PUBLISH;

    public WorkflowByIdQuery(String id) {
        this.id = id;
    }

    public WorkflowByIdQuery(String id, String version) {
        this.id = id;
        this.version = version;
    }

    public WorkflowByIdQuery(String id, VersionMode versionMode) {
        this.id = id;
        this.versionMode = versionMode;
    }

    @Getter
    @AllArgsConstructor
    public static class Response {
        private Workflow workflow;
    }

}