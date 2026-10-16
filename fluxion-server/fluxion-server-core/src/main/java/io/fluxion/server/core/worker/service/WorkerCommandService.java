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

package io.fluxion.server.core.worker.service;

import io.fluxion.server.core.worker.Worker;
import io.fluxion.server.core.worker.cmd.WorkerHeartbeatCmd;
import io.fluxion.server.core.worker.cmd.WorkerSaveCmd;
import io.fluxion.server.core.worker.cmd.WorkerSliceOfflineCmd;
import io.fluxion.server.core.worker.converter.WorkerConverter;
import io.fluxion.server.core.worker.metric.WorkerMetric;
import io.fluxion.server.infrastructure.dao.entity.WorkerEntity;
import io.fluxion.server.infrastructure.dao.entity.WorkerExecutorEntity;
import io.fluxion.server.infrastructure.dao.entity.WorkerMetricEntity;
import io.fluxion.server.infrastructure.dao.repository.WorkerEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.WorkerExecutorEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.WorkerMetricEntityRepo;
import io.fluxion.server.infrastructure.exception.ErrorCode;
import io.fluxion.server.infrastructure.exception.PlatformException;
import io.fluxion.server.infrastructure.id.cmd.IDGenerateCmd;
import io.fluxion.server.infrastructure.id.data.IDType;
import io.fluxion.server.infrastructure.tag.TagRefType;
import io.fluxion.server.infrastructure.tag.cmd.TagsSaveByRefCmd;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import io.limbo.cqrs.spring.annotation.CommandHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import io.limbo.cqrs.core.commandhandling.Cmd;

/**
 * @author Devil
 */
@Service
public class WorkerCommandService {

    @Resource
    private WorkerEntityRepo workerEntityRepo;
    @Resource
    private WorkerExecutorEntityRepo workerExecutorEntityRepo;
    @Resource
    private WorkerMetricEntityRepo workerMetricEntityRepo;
    @Resource
    private EntityManager entityManager;

    @Transactional
    @CommandHandler
    public WorkerSaveCmd.Response handle(WorkerSaveCmd cmd) {
        Worker worker = cmd.getWorker();
        String workerId = worker.id();

        WorkerEntity entity = WorkerConverter.toWorkerEntity(worker);
        Objects.requireNonNull(entity);

        workerEntityRepo.saveAndFlush(entity);

        // Metric 存储
        WorkerMetric metric = worker.getMetric();
        WorkerMetricEntity metricEntity = WorkerConverter.toMetricEntity(workerId, metric);
        workerMetricEntityRepo.saveAndFlush(Objects.requireNonNull(metricEntity));

        // Executors 存储
        workerExecutorEntityRepo.deleteByWorkerId(workerId);
        List<WorkerExecutorEntity> executorEntities = WorkerConverter.toExecutorEntities(workerId, worker.getExecutors());
        if (CollectionUtils.isNotEmpty(executorEntities)) {
            workerExecutorEntityRepo.saveAllAndFlush(executorEntities);
        }

        // Tags 存储
        Cmd.send(new TagsSaveByRefCmd(workerId, TagRefType.WORKER, worker.getTags()));

        return new WorkerSaveCmd.Response(worker.id());
    }

    @Transactional
    @CommandHandler
    public void handle(WorkerHeartbeatCmd cmd) {
        WorkerEntity entity = workerEntityRepo.findById(cmd.getWorkerId()).orElse(null);
        if (entity == null) {
            throw new PlatformException(ErrorCode.PARAM_ERROR, "worker not found by id:" + cmd.getWorkerId());
        }
        entityManager.createQuery("update WorkerEntity " +
                "set status = :status " +
                "where workerId = :workerId"
            )
            .setParameter("status", Worker.Status.ONLINE.status)
            .setParameter("workerId", cmd.getWorkerId())
            .executeUpdate();

        // 必须走显式条件更新：saveAndFlush(merge) 会把分配主键 detached 实体的
        // createdAt=null 覆盖进更新集，使每次心跳 UPDATE 因 NOT NULL 失败（同 Broker 心跳）
        WorkerMetric metric = cmd.getMetric();
        int updated = workerMetricEntityRepo.heartbeat(
            cmd.getWorkerId(),
            metric.getCpuProcessors(),
            metric.getCpuLoad(),
            metric.getFreeMemory(),
            metric.getAvailableQueueNum(),
            metric.getLastHeartbeatAt(),
            metric.getLastHeartbeatAt());
        if (updated == 0) {
            WorkerMetricEntity metricEntity = WorkerConverter.toMetricEntity(cmd.getWorkerId(), metric);
            workerMetricEntityRepo.saveAndFlush(metricEntity);
        }
    }

    @Transactional
    @CommandHandler
    public WorkerSliceOfflineCmd.Response handle(WorkerSliceOfflineCmd cmd) {
        List<WorkerMetricEntity> metricEntities = entityManager.createQuery(
                "select m from WorkerMetricEntity m, WorkerEntity w " +
                    "where m.workerId = w.workerId and m.deleted = false and w.deleted = false " +
                    "and w.status = :online and m.lastHeartbeatAt < :cutoff " +
                    "and (:cursorHeartbeatAt is null or m.lastHeartbeatAt > :cursorHeartbeatAt " +
                    "or (m.lastHeartbeatAt = :cursorHeartbeatAt and m.workerId > :cursorWorkerId)) " +
                    "order by m.lastHeartbeatAt asc, m.workerId asc", WorkerMetricEntity.class)
            .setParameter("online", Worker.Status.ONLINE.status)
            .setParameter("cutoff", cmd.getCutoff())
            .setParameter("cursorHeartbeatAt", cmd.getCursorHeartbeatAt())
            .setParameter("cursorWorkerId", cmd.getCursorWorkerId())
            .setMaxResults(cmd.getLimit())
            .getResultList();
        if (CollectionUtils.isEmpty(metricEntities)) {
            return new WorkerSliceOfflineCmd.Response(java.util.Collections.emptyList(), null, null);
        }

        List<String> workerIds = metricEntities.stream()
            .filter(metric -> entityManager.createQuery(
                    "update WorkerEntity w set w.status = :offline " +
                        "where w.workerId = :workerId and w.status = :online and w.deleted = false " +
                        "and exists (select 1 from WorkerMetricEntity m where m.workerId = w.workerId " +
                        "and m.deleted = false and m.lastHeartbeatAt < :cutoff)")
                .setParameter("offline", Worker.Status.OFFLINE.status)
                .setParameter("online", Worker.Status.ONLINE.status)
                .setParameter("workerId", metric.getWorkerId())
                .setParameter("cutoff", cmd.getCutoff())
                .executeUpdate() == 1)
            .map(WorkerMetricEntity::getWorkerId)
            .collect(Collectors.toList());

        WorkerMetricEntity last = metricEntities.get(metricEntities.size() - 1);
        return new WorkerSliceOfflineCmd.Response(workerIds, last.getLastHeartbeatAt(), last.getWorkerId());
    }

}
