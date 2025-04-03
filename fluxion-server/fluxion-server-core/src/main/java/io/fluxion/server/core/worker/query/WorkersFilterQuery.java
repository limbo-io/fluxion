/*
 * Copyright 2025-2030 limbo-io Team (https://github.com/limbo-io).
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

package io.fluxion.server.core.worker.query;

import io.fluxion.server.core.executor.option.DispatchOption;
import io.fluxion.server.core.worker.Worker;
import io.fluxion.server.infrastructure.cqrs.IQuery;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * @author Devil
 */
@Getter
@AllArgsConstructor
public class WorkersFilterQuery implements IQuery<WorkersFilterQuery.Response> {
    private String appId;
    private String executorName;
    private DispatchOption dispatchOption;
    private boolean filterResource;
    private boolean loadBalanceSelect;

    @Getter
    @AllArgsConstructor
    public static class Response {
        private List<Worker> workers;
    }
}
