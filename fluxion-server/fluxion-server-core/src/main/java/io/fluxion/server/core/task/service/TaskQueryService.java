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

package io.fluxion.server.core.task.service;

import io.fluxion.server.core.task.query.TaskCountByStatusQuery;
import io.fluxion.server.infrastructure.dao.repository.TaskEntityRepo;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
@Service
public class TaskQueryService {

    @Resource
    private TaskEntityRepo taskEntityRepo;

    @Resource
    private EntityManager entityManager;

    @QueryHandler
    public TaskCountByStatusQuery.Response handle(TaskCountByStatusQuery query) {
        long count = taskEntityRepo.countByExecutionIdAndRefIdInAndStatusIn(
            query.getExecutionId(), query.getRefIds(), query.getStatuses().stream().map(s -> s.value).collect(Collectors.toList())
        );
        for (String refId : query.getRefIds()) {
            System.out.println(refId + " " + taskEntityRepo.countByExecutionIdAndRefIdInAndStatusIn(
                query.getExecutionId(), Collections.singletonList(refId), query.getStatuses().stream().map(s -> s.value).collect(Collectors.toList())
            ));
        }
        return new TaskCountByStatusQuery.Response(count);
    }
}
