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

import io.fluxion.remote.core.constants.WorkerStatus;
import io.fluxion.server.core.worker.Worker;
import io.fluxion.server.core.worker.cmd.WorkerHeartbeatCmd;
import io.fluxion.server.core.worker.cmd.WorkerSaveCmd;
import io.fluxion.server.core.worker.converter.WorkerConverter;
import io.fluxion.server.core.worker.metric.WorkerMetric;
import io.fluxion.server.infrastructure.dao.entity.TagEntity;
import io.fluxion.server.infrastructure.dao.entity.WorkerEntity;
import io.fluxion.server.infrastructure.dao.entity.WorkerExecutorEntity;
import io.fluxion.server.infrastructure.dao.entity.WorkerMetricEntity;
import io.fluxion.server.infrastructure.dao.repository.TagEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.WorkerEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.WorkerExecutorEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.WorkerMetricEntityRepo;
import io.fluxion.server.infrastructure.exception.ErrorCode;
import io.fluxion.server.infrastructure.exception.PlatformException;
import io.fluxion.server.infrastructure.tag.TagRefType;
import org.apache.commons.collections4.CollectionUtils;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import java.util.List;
import java.util.Objects;

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
    private TagEntityRepo tagEntityRepo;
    @Resource
    private EntityManager entityManager;

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
        tagEntityRepo.deleteById_RefIdAndId_RefType(workerId, TagRefType.WORKER.value);
        List<TagEntity> tagEntities = WorkerConverter.toTagEntities(workerId, worker);
        if (CollectionUtils.isNotEmpty(tagEntities)) {
            tagEntityRepo.saveAllAndFlush(tagEntities);
        }

        return new WorkerSaveCmd.Response(worker.id());
    }

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
            .setParameter("status", WorkerStatus.RUNNING.status)
            .setParameter("workerId", cmd.getWorkerId())
            .executeUpdate();


        WorkerMetricEntity metricEntity = WorkerConverter.toMetricEntity(cmd.getWorkerId(), cmd.getMetric());
        workerMetricEntityRepo.saveAndFlush(metricEntity);
    }

}
