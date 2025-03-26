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

package io.fluxion.server.core.broker.converter;

import io.fluxion.remote.core.api.dto.NodeDTO;
import io.fluxion.remote.core.api.dto.SystemInfoDTO;
import io.fluxion.remote.core.api.dto.WorkerTagDTO;
import io.fluxion.remote.core.api.request.broker.WorkerRegisterRequest;
import io.fluxion.remote.core.cluster.Node;
import io.fluxion.remote.core.constants.Protocol;
import io.fluxion.remote.core.constants.WorkerStatus;
import io.fluxion.server.core.broker.BrokerNode;
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
public class BrokerClientConverter {

    public static Worker toWorker(String appId, WorkerRegisterRequest request) {
        WorkerMetric metric = toMetric(request.getSystemInfo(), request.getAvailableQueueNum(), System.currentTimeMillis());
        Map<String, Set<String>> tags = CollectionUtils.isEmpty(request.getTags()) ? Collections.emptyMap() : request.getTags().stream()
            .collect(Collectors.groupingBy(WorkerTagDTO::getName, mapping(WorkerTagDTO::getValue, toSet())));
        List<WorkerExecutor> executors = request.getExecutors().stream()
            .map(e -> WorkerExecutor.builder()
                .name(e.getName())
                .build()
            ).collect(Collectors.toList());
        return new Worker(
            appId, request.getHost(), request.getPort(), Protocol.parse(request.getProtocol()),
            executors, tags, metric, WorkerStatus.RUNNING, true
        );
    }

    public static WorkerMetric toMetric(SystemInfoDTO systemInfoDTO, int availableQueueNum, Long lastHeartbeatAt) {
        return new WorkerMetric(
            systemInfoDTO.getCpuProcessors(),
            systemInfoDTO.getCpuLoad(),
            systemInfoDTO.getFreeMemory(),
            availableQueueNum,
            lastHeartbeatAt
        );
    }

    public static NodeDTO toDTO(Node node) {
        if (node == null) {
            return null;
        }
        NodeDTO dto = new NodeDTO();
        dto.setProtocol(node.protocol().getValue());
        dto.setHost(node.host());
        dto.setPort(node.port());
        return dto;
    }

    public static List<NodeDTO> toBrokerNodeDTO(List<BrokerNode> nodes) {
        if (CollectionUtils.isEmpty(nodes)) {
            return Collections.emptyList();
        }
        return nodes.stream().map(BrokerClientConverter::toDTO).collect(Collectors.toList());
    }

}
