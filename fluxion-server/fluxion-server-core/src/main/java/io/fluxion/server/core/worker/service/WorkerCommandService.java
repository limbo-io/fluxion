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
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.dao.entity.WorkerEntity;
import io.fluxion.server.infrastructure.dao.entity.WorkerExecutorEntity;
import io.fluxion.server.infrastructure.dao.entity.WorkerMetricEntity;
import io.fluxion.server.infrastructure.dao.repository.WorkerEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.WorkerExecutorEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.WorkerMetricEntityRepo;
import io.fluxion.server.infrastructure.exception.ErrorCode;
import io.fluxion.server.infrastructure.exception.PlatformException;
import io.fluxion.server.infrastructure.tag.TagRefType;
import io.fluxion.server.infrastructure.tag.cmd.TagsSaveByRefCmd;
import io.fluxion.server.infrastructure.utils.JpaHelper;
import org.apache.commons.collections4.CollectionUtils;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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


        WorkerMetricEntity metricEntity = WorkerConverter.toMetricEntity(cmd.getWorkerId(), cmd.getMetric());
        workerMetricEntityRepo.saveAndFlush(metricEntity);
    }

    @Transactional
    @CommandHandler
    public WorkerSliceOfflineCmd.Response handle(WorkerSliceOfflineCmd cmd) {
        List<WorkerMetricEntity> metricEntities = workerMetricEntityRepo.findByLastHeartbeatAtBetween(
            cmd.getStartTime(), cmd.getEndTime(), JpaHelper.pageable(0, cmd.getLimit())
        );
        if (CollectionUtils.isEmpty(metricEntities)) {
            return new WorkerSliceOfflineCmd.Response(0);
        }
        entityManager.createQuery("update WorkerEntity " +
                "set status = :status " +
                "where workerId in :workerIds"
            )
            .setParameter("status", Worker.Status.OFFLINE.status)
            .setParameter("workerIds", metricEntities.stream()
                .map(WorkerMetricEntity::getWorkerId)
                .collect(Collectors.toList())
            )
            .executeUpdate();
        return new WorkerSliceOfflineCmd.Response(metricEntities.size());
    }

}
