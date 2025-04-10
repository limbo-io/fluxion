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
import io.fluxion.server.core.execution.ExecutableType;
import io.fluxion.server.core.execution.Execution;
import io.fluxion.server.core.execution.ExecutionStatus;
import io.fluxion.server.core.execution.query.ExecutableByIdQuery;
import io.fluxion.server.core.execution.query.ExecutionByIdQuery;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.dao.entity.ExecutionEntity;
import io.fluxion.server.infrastructure.dao.repository.ExecutionEntityRepo;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @author Devil
 */
@Service
public class ExecutionQueryService {

    @Resource
    private ExecutionEntityRepo executionEntityRepo;

    @QueryHandler
    public ExecutionByIdQuery.Response handle(ExecutionByIdQuery query) {
        ExecutionEntity entity = executionEntityRepo.findById(query.getExecutionId()).orElse(null);
        if (entity == null) {
            return new ExecutionByIdQuery.Response(null);
        }
        Executable executable = Query.query(new ExecutableByIdQuery(
            entity.getExecutableId(), ExecutableType.parse(entity.getExecutableType()), entity.getExecutableVersion()
        )).getExecutable();
        if (executable == null) {
            return new ExecutionByIdQuery.Response(null);
        }
        Execution execution = new Execution(entity.getExecutionId(), executable, ExecutionStatus.parse(entity.getStatus()));
        return new ExecutionByIdQuery.Response(execution);
    }

}
