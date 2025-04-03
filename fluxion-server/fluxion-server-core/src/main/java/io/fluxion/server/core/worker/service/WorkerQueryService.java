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

import com.google.common.collect.Lists;
import io.fluxion.server.core.executor.option.DispatchOption;
import io.fluxion.server.core.worker.Worker;
import io.fluxion.server.core.worker.converter.WorkerConverter;
import io.fluxion.server.core.worker.dispatch.WorkerFilter;
import io.fluxion.server.core.worker.query.WorkerByAppQuery;
import io.fluxion.server.core.worker.query.WorkerByIdsQuery;
import io.fluxion.server.core.worker.query.WorkersFilterQuery;
import io.fluxion.server.core.worker.selector.WorkerSelectInvocation;
import io.fluxion.server.core.worker.selector.WorkerSelector;
import io.fluxion.server.core.worker.selector.WorkerSelectorFactory;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.dao.entity.WorkerEntity;
import io.fluxion.server.infrastructure.dao.entity.WorkerExecutorEntity;
import io.fluxion.server.infrastructure.dao.entity.WorkerMetricEntity;
import io.fluxion.server.infrastructure.dao.repository.WorkerEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.WorkerExecutorEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.WorkerMetricEntityRepo;
import io.fluxion.server.infrastructure.tag.Tag;
import io.fluxion.server.infrastructure.tag.TagRefType;
import io.fluxion.server.infrastructure.tag.query.TagsByRefsQuery;
import org.apache.commons.collections4.CollectionUtils;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

    private static final WorkerSelectorFactory WORKER_SELECTOR_FACTORY = new WorkerSelectorFactory();

    @QueryHandler
    public WorkerByAppQuery.Response handle(WorkerByAppQuery query) {
        List<WorkerEntity> workerEntities = workerEntityRepo.findByAppIdAndStatusIn(
            query.getAppId(), query.getStatuses().stream().map(s -> s.status).collect(Collectors.toList())
        );
        return new WorkerByAppQuery.Response(convertWorkerByEntities(workerEntities));
    }

    @QueryHandler
    public WorkerByIdsQuery.Response handle(WorkerByIdsQuery query) {
        List<WorkerEntity> workerEntities = workerEntityRepo.findAllById(query.getIds());
        return new WorkerByIdsQuery.Response(convertWorkerByEntities(workerEntities));
    }

    private List<Worker> convertWorkerByEntities(List<WorkerEntity> workerEntities) {
        if (CollectionUtils.isEmpty(workerEntities)) {
            return Collections.emptyList();
        }
        List<String> workerIds = workerEntities.stream().map(WorkerEntity::getWorkerId).collect(Collectors.toList());
        List<WorkerExecutorEntity> executorEntities = workerExecutorEntityRepo.findById_WorkerIdIn(workerIds);
        Map<String, List<Tag>> refTags = Query.query(new TagsByRefsQuery(workerIds, TagRefType.WORKER)).getRefTags();
        List<WorkerMetricEntity> metricEntities = workerMetricEntityRepo.findByWorkerIdIn(workerIds);
        return WorkerConverter.toWorkers(workerEntities, executorEntities, refTags, metricEntities);
    }

    @QueryHandler
    public WorkersFilterQuery.Response handle(WorkersFilterQuery query) {
        String executorName = query.getExecutorName();
        DispatchOption dispatchOption = query.getDispatchOption();
        List<Worker> workers = Query.query(new WorkerByAppQuery(
                query.getAppId(), Lists.newArrayList(Worker.Status.ONLINE)
            )).getWorkers().stream()
            .filter(Worker::isEnabled)
            .collect(Collectors.toList());
        // 广播的应该不关心资源大小 在配置的时候直接处理
        WorkerFilter workerFilter = new WorkerFilter(workers)
            .filterExecutor(executorName)
            .filterTags(dispatchOption.getTagFilters());
        if (query.isFilterResource()) {
            workerFilter = workerFilter.filterResources(dispatchOption.getCpuRequirement(), dispatchOption.getRamRequirement());
        }
        List<Worker> filterWorkers = workerFilter.get();
        if (query.isLoadBalanceSelect()) {
            WorkerSelectInvocation invocation = new WorkerSelectInvocation(executorName, null);
            WorkerSelector workerSelector = WORKER_SELECTOR_FACTORY.newSelector(dispatchOption.getLoadBalanceType());
            Worker worker = workerSelector.select(invocation, filterWorkers);
            return new WorkersFilterQuery.Response(Collections.singletonList(worker));
        } else  {
            return new WorkersFilterQuery.Response(filterWorkers);
        }
    }

}
