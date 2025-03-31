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

package io.fluxion.server.core.worker.converter;

import io.fluxion.remote.core.constants.Protocol;
import io.fluxion.server.core.worker.Worker;
import io.fluxion.server.core.worker.executor.WorkerExecutor;
import io.fluxion.server.core.worker.metric.WorkerMetric;
import io.fluxion.server.infrastructure.dao.entity.WorkerEntity;
import io.fluxion.server.infrastructure.dao.entity.WorkerExecutorEntity;
import io.fluxion.server.infrastructure.dao.entity.WorkerMetricEntity;
import io.fluxion.server.infrastructure.tag.Tag;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
public class WorkerConverter {

    public static List<Worker> toWorkers(List<WorkerEntity> workerEntities, List<WorkerExecutorEntity> executorEntities, Map<String, List<Tag>> refTags,
                                         List<WorkerMetricEntity> metricEntities) {
        if (CollectionUtils.isEmpty(workerEntities)) {
            return Collections.emptyList();
        }

        Map<String, WorkerMetricEntity> metricEntityMap = metricEntities.stream().collect(Collectors.toMap(WorkerMetricEntity::getWorkerId, m -> m));

        Map<String, List<WorkerExecutorEntity>> executorEntityMap = executorEntities.stream().collect(Collectors.groupingBy(e -> e.getId().getWorkerId()));

        List<Worker> workers = new ArrayList<>();
        for (WorkerEntity workerEntity : workerEntities) {
            String workerId = workerEntity.getWorkerId();
            workers.add(toWorker(
                workerEntity,
                executorEntityMap.get(workerId),
                refTags.get(workerId),
                metricEntityMap.get(workerId)
            ));
        }
        return workers;
    }

    /**
     * 将持久化对象{@link WorkerEntity}转换为领域对象{@link Worker}
     *
     * @param entity {@link WorkerEntity}持久化对象
     * @return {@link Worker}领域对象
     */
    public static Worker toWorker(WorkerEntity entity, List<WorkerExecutorEntity> executorEntities, List<Tag> tags,
                                  WorkerMetricEntity metricEntity) {
        // 已删除则不返回
        if (entity == null || entity.isDeleted()) {
            return null;
        }
        List<WorkerExecutor> executors = toExecutors(executorEntities);
        WorkerMetric metric = toMetric(metricEntity);
        return new Worker(
            entity.getAppId(), entity.getHost(), entity.getPort(), Protocol.parse(entity.getProtocol()),
            executors, tags, metric, Worker.Status.parse(entity.getStatus()), entity.isEnabled()
        );
    }

    /**
     * 将领域对象{@link Worker}转换为持久化对象{@link WorkerEntity}
     *
     * @param worker {@link Worker}领域对象
     * @return {@link WorkerEntity}持久化对象
     */
    public static WorkerEntity toWorkerEntity(Worker worker) {
        WorkerEntity po = new WorkerEntity();
        po.setWorkerId(worker.id());
        po.setProtocol(worker.getProtocol().getValue());
        po.setHost(worker.getHost());
        po.setPort(worker.getPort());
        po.setAppId(worker.getAppId());
        po.setStatus(worker.getStatus().status);
        po.setEnabled(true);
        return po;
    }

    /**
     * 将{@link WorkerMetricEntity}持久化对象转换为{@link WorkerMetric}值对象
     *
     * @param entity {@link WorkerMetricEntity}持久化对象
     * @return {@link WorkerMetric}值对象
     */
    public static WorkerMetric toMetric(WorkerMetricEntity entity) {
        return WorkerMetric.builder()
            .cpuProcessors(entity.getCpuProcessors())
            .cpuLoad(entity.getCpuLoad())
            .freeMemory(entity.getFreeMemory())
            .availableQueueNum(entity.getAvailableQueueNum())
            .lastHeartbeatAt(entity.getLastHeartbeatAt())
            .build();
    }

    /**
     * 将{@link WorkerMetric}值对象转换为{@link WorkerMetricEntity}持久化对象
     *
     * @param metric {@link WorkerMetric}值对象
     * @return {@link WorkerMetricEntity}持久化对象
     */
    public static WorkerMetricEntity toMetricEntity(String workerId, WorkerMetric metric) {
        WorkerMetricEntity entity = new WorkerMetricEntity();
        entity.setWorkerId(workerId);
        entity.setCpuProcessors(metric.getCpuProcessors());
        entity.setCpuLoad(metric.getCpuLoad());
        entity.setFreeMemory(metric.getFreeMemory());
        entity.setAvailableQueueNum(metric.getAvailableQueueNum());
        entity.setLastHeartbeatAt(metric.getLastHeartbeatAt());
        return entity;
    }

    /**
     * 将执行器持久化对象转为领域模型
     */
    public static List<WorkerExecutor> toExecutors(List<WorkerExecutorEntity> entities) {
        if (CollectionUtils.isEmpty(entities)) {
            return Collections.emptyList();
        }

        return entities.stream()
            .map(entity -> WorkerExecutor.builder()
                .name(entity.getId().getName())
                .build())
            .collect(Collectors.toList());
    }

    /**
     * 提取 Worker 中的 executors，转为持久化对象列表
     */
    public static List<WorkerExecutorEntity> toExecutorEntities(String workerId, List<WorkerExecutor> executors) {
        if (CollectionUtils.isEmpty(executors)) {
            return Collections.emptyList();
        }
        return executors.stream()
            .map(executor -> {
                WorkerExecutorEntity entity = new WorkerExecutorEntity();
                entity.setId(new WorkerExecutorEntity.ID(
                    workerId, executor.getName()
                ));
                return entity;
            })
            .collect(Collectors.toList());
    }

}
