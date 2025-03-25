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

package io.fluxion.server.core.worker.service;

import io.fluxion.server.core.worker.converter.WorkerConverter;
import io.fluxion.server.core.worker.query.WorkerByAppQuery;
import io.fluxion.server.infrastructure.dao.entity.TagEntity;
import io.fluxion.server.infrastructure.dao.entity.WorkerEntity;
import io.fluxion.server.infrastructure.dao.entity.WorkerExecutorEntity;
import io.fluxion.server.infrastructure.dao.entity.WorkerMetricEntity;
import io.fluxion.server.infrastructure.dao.repository.TagEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.WorkerEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.WorkerExecutorEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.WorkerMetricEntityRepo;
import io.fluxion.server.infrastructure.tag.TagRefType;
import org.apache.commons.collections4.CollectionUtils;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
@Service
public class WorkerQueryService {

    @Resource
    private WorkerEntityRepo workerEntityRepo;
    @Resource
    private WorkerExecutorEntityRepo workerExecutorEntityRepo;
    @Resource
    private WorkerMetricEntityRepo workerMetricEntityRepo;
    @Resource
    private TagEntityRepo tagEntityRepo;

    @QueryHandler
    public WorkerByAppQuery.Response handle(WorkerByAppQuery query) {
        List<WorkerEntity> workerEntities = workerEntityRepo.findByAppId(query.getAppId());
        if (CollectionUtils.isEmpty(workerEntities)) {
            return new WorkerByAppQuery.Response(Collections.emptyList());
        }
        List<String> workerIds = workerEntities.stream().map(WorkerEntity::getWorkerId).collect(Collectors.toList());
        List<WorkerExecutorEntity> executorEntities = workerExecutorEntityRepo.findById_WorkerIdIn(workerIds);
        List<TagEntity> tagEntities = tagEntityRepo.findById_RefIdInAndId_RefType(workerIds, TagRefType.WORKER.value);
        List<WorkerMetricEntity> metricEntities = workerMetricEntityRepo.findByWorkerIdIn(workerIds);
        return new WorkerByAppQuery.Response(
            WorkerConverter.toWorkers(workerEntities, executorEntities, tagEntities, metricEntities)
        );
    }
}
