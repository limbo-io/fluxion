/*
 * Copyright 2024-2030 fluxion-io Team (https://github.com/fluxion-io).
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

package io.fluxion.server.core.worker.handler;

import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.server.core.app.cmd.AppRegisterCmd;
import io.fluxion.server.core.worker.Worker;
import io.fluxion.server.core.worker.WorkerStatus;
import io.fluxion.server.core.worker.cmd.WorkerRegisterCmd;
import io.fluxion.server.core.worker.executor.WorkerExecutor;
import io.fluxion.server.core.worker.metric.WorkerMetric;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.dao.entity.TagEntity;
import io.fluxion.server.infrastructure.dao.entity.WorkerEntity;
import io.fluxion.server.infrastructure.dao.repository.TagEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.WorkerEntityRepo;
import io.fluxion.server.infrastructure.tag.TagRefType;
import org.apache.commons.lang3.StringUtils;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
@Component
public class WorkerHandler {

    @Resource
    private WorkerEntityRepo workerEntityRepo;

    @Resource
    private TagEntityRepo tagEntityRepo;

    @CommandHandler
    public WorkerRegisterCmd.Response handle(WorkerRegisterCmd cmd) {
        Worker worker = cmd.getWorker();
        if (StringUtils.isNotBlank(worker.id())) {
            return new WorkerRegisterCmd.Response(worker.id());
        }

        String appId = Cmd.send(new AppRegisterCmd(cmd.getAppName())).getId();

        WorkerEntity workerEntity = workerEntityRepo.findByAppIdAndHostAndPortAndDeleted(worker.getAppId(), worker.getHost(), worker.getPort(), false)
            .orElse(newEntity(worker, appId));
        workerEntityRepo.saveAndFlush(workerEntity);

        tagEntityRepo.saveAllAndFlush(tagEntities(worker));
        return new WorkerRegisterCmd.Response(worker.id());
    }

    private WorkerEntity newEntity(Worker worker, String appId) {
        WorkerEntity entity = new WorkerEntity();
        entity.setId(new WorkerEntity.ID(
            appId, worker.getHost(), worker.getPort()
        ));
        entity.setProtocol(worker.getProtocol().value);
        entity.setStatus(WorkerStatus.RUNNING.status);
        entity.setEnabled(true);

        WorkerMetric metric = worker.getMetric();
        entity.setAvailableQueueNum(metric.getAvailableQueueNum());
        entity.setLastHeartbeatAt(metric.getLastHeartbeatAt());

        List<WorkerExecutor> executors = worker.getExecutors();
        entity.setExecutors(JacksonUtils.toJSONString(executors));
        return entity;
    }

    private List<TagEntity> tagEntities(Worker worker) {
        Map<String, Set<String>> tags = worker.tags();
        return tags.entrySet().stream().flatMap(entry -> {
            String name = entry.getKey();
            return entry.getValue().stream().map(v -> {
                TagEntity entity = new TagEntity();
                entity.setId(new TagEntity.ID(
                    worker.id(),
                    TagRefType.WORKER.value,
                    name,
                    v
                ));
                return entity;
            });
        }).collect(Collectors.toList());
    }

}
