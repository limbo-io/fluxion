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

package io.fluxion.server.core.broker.converter;

import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.remote.core.api.dto.WorkerTagDTO;
import io.fluxion.remote.core.api.request.WorkerRegisterRequest;
import io.fluxion.remote.core.constants.Protocol;
import io.fluxion.server.core.worker.Worker;
import io.fluxion.server.core.worker.executor.WorkerExecutor;
import io.fluxion.server.core.worker.metric.WorkerMetric;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;

/**
 * @author Devil
 */
public class BrokerRpcConverter {

    public static Worker toWorker(WorkerRegisterRequest request) {
        Worker worker = new Worker();
        worker.setHost(request.getHost());
        worker.setPort(request.getPort());
        worker.setProtocol(Protocol.parse(request.getProtocol()));

        WorkerMetric metric = new WorkerMetric(
            request.getMetric().getCpuProcessors(),
            request.getMetric().getCpuLoad(),
            request.getMetric().getAvailableRAM(),
            request.getMetric().getAvailableQueueLimit(),
            TimeUtils.currentLocalDateTime()
        );
        worker.setMetric(metric);

        Map<String, Set<String>> tags = CollectionUtils.isEmpty(request.getTags()) ? Collections.emptyMap() : request.getTags().stream()
            .collect(Collectors.groupingBy(WorkerTagDTO::getName, mapping(WorkerTagDTO::getValue, toSet())));
        worker.setTags(tags);

        List<WorkerExecutor> executors = request.getExecutors().stream()
            .map(e -> WorkerExecutor.builder()
                .name(e.getName())
                .build()
            ).collect(Collectors.toList());
        worker.setExecutors(executors);

        return worker;
    }
}
