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
import io.fluxion.server.core.worker.WorkerRepository;
import io.fluxion.server.core.worker.cmd.WorkerHeartbeatCmd;
import io.fluxion.server.core.worker.cmd.WorkerRegisterCmd;
import io.fluxion.server.infrastructure.exception.ErrorCode;
import io.fluxion.server.infrastructure.exception.PlatformException;
import io.fluxion.server.infrastructure.tag.Tag;
import org.apache.commons.lang3.StringUtils;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
@Service
public class WorkerCommandService {

    @Resource
    private WorkerRepository workerRepository;

    @CommandHandler
    public WorkerRegisterCmd.Response handle(WorkerRegisterCmd cmd) {
        Worker worker = cmd.getWorker();
        if (StringUtils.isNotBlank(worker.id())) {
            return new WorkerRegisterCmd.Response(worker.id());
        }
        workerRepository.save(worker);
        return new WorkerRegisterCmd.Response(worker.id());
    }

    @CommandHandler
    public void handle(WorkerHeartbeatCmd cmd) {
        Worker worker = workerRepository.get(cmd.getWorkerId());
        if (worker == null) {
            throw new PlatformException(ErrorCode.PARAM_ERROR, "worker not found by id:" + cmd.getWorkerId());
        }
        worker.setMetric(cmd.getMetric());
        worker.setStatus(WorkerStatus.RUNNING);
        workerRepository.save(worker);
    }

    private List<Tag> tags(Worker worker) {
        Map<String, Set<String>> tags = worker.tags();
        return tags.entrySet().stream().flatMap(entry -> {
            String name = entry.getKey();
            return entry.getValue().stream().map(v -> new Tag(name, v));
        }).collect(Collectors.toList());
    }

}
