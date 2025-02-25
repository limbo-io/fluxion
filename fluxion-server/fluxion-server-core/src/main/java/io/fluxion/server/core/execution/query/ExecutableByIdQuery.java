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

package io.fluxion.server.core.execution.query;

import io.fluxion.server.core.execution.Executable;
import io.fluxion.server.core.trigger.TriggerRefType;
import io.fluxion.server.infrastructure.cqrs.IQuery;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Devil
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutableByIdQuery implements IQuery<ExecutableByIdQuery.Response> {

    private String id;

    private TriggerRefType type;

    @Getter
    @AllArgsConstructor
    public static class Response {
        private Executable executable;
    }

}