/*
 * Copyright 2025-2030 fluxion-io Team (https://github.com/fluxion-io).
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

package io.fluxion.server.core.execution.service;

import io.fluxion.server.core.execution.Executable;
import io.fluxion.server.core.execution.ExecuteConfig;
import io.fluxion.server.core.execution.config.ExecutorExecuteConfig;
import io.fluxion.server.core.execution.query.ExecutableByIdQuery;
import io.fluxion.server.core.executor.Executor;
import io.fluxion.server.core.flow.query.FlowByIdQuery;
import io.fluxion.server.core.trigger.Trigger;
import io.fluxion.server.core.trigger.query.TriggerByIdQuery;
import io.fluxion.server.infrastructure.cqrs.Query;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Service;

/**
 * @author Devil
 */
@Service
public class ExecutableQueryService {

    @QueryHandler
    public ExecutableByIdQuery.Response handle(ExecutableByIdQuery query) {
        Executable executable = null;
        switch (query.getType()) {
            case FLOW:
                executable = Query.query(new FlowByIdQuery(query.getId(), query.getVersion())).getFlow();
                break;
            case EXECUTOR:
                Trigger trigger = Query.query(new TriggerByIdQuery(query.getId())).getTrigger();
                ExecuteConfig executeConfig = trigger.getConfig().getExecuteConfig();
                ExecutorExecuteConfig executorExecuteConfig = (ExecutorExecuteConfig) executeConfig;
                executable = Executor.of(executorExecuteConfig.getExecutor(), executorExecuteConfig.getRetryOption(), executorExecuteConfig.getOvertimeOption());
                break;
        }
        return new ExecutableByIdQuery.Response(executable);
    }

}
