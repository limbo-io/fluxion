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

import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.remote.core.api.dto.NodeDTO;
import io.fluxion.remote.core.api.dto.SystemInfoDTO;
import io.fluxion.remote.core.api.dto.TagDTO;
import io.fluxion.remote.core.api.dto.TaskMonitorDTO;
import io.fluxion.remote.core.api.request.broker.WorkerRegisterRequest;
import io.fluxion.remote.core.cluster.BaseNode;
import io.fluxion.remote.core.cluster.Node;
import io.fluxion.remote.core.constants.Protocol;
import io.fluxion.server.core.broker.BrokerNode;
import io.fluxion.server.core.job.TaskMonitor;
import io.fluxion.server.core.worker.Worker;
import io.fluxion.server.core.worker.executor.WorkerExecutor;
import io.fluxion.server.core.worker.metric.WorkerMetric;
import io.fluxion.server.infrastructure.tag.Tag;
import org.apache.commons.collections4.CollectionUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
public class BrokerClientConverter {

    public static Worker toWorker(String appId, WorkerRegisterRequest request) {
        WorkerMetric metric = toMetric(request.getSystemInfo(), request.getAvailableQueueNum(), TimeUtils.currentLocalDateTime());
        List<Tag> tags = convert(request.getTags());
        List<WorkerExecutor> executors = request.getExecutors().stream()
            .map(e -> WorkerExecutor.builder()
                .name(e.getName())
                .build()
            ).collect(Collectors.toList());
        String id = request.getProtocol() + "://" + request.getHost() + ":" + request.getPort();
        return new Worker(
            id, appId, request.getHost(), request.getPort(), Protocol.parse(request.getProtocol()),
            executors, tags, metric, Worker.Status.ONLINE, true
        );
    }

    public static WorkerMetric toMetric(SystemInfoDTO systemInfoDTO, int availableQueueNum, LocalDateTime lastHeartbeatAt) {
        return new WorkerMetric(
            systemInfoDTO.getCpuProcessors(),
            systemInfoDTO.getCpuLoad(),
            systemInfoDTO.getFreeMemory(),
            availableQueueNum,
            lastHeartbeatAt
        );
    }

    public static List<Tag> convert(List<TagDTO> dtos) {
        if (CollectionUtils.isEmpty(dtos)) {
            return Collections.emptyList();
        }
        return dtos.stream()
            .map(BrokerClientConverter::convert)
            .collect(Collectors.toList());
    }

    public static Tag convert(TagDTO dto) {
        return new Tag(dto.getName(), dto.getValue());
    }

    public static NodeDTO toDTO(Node node) {
        if (node == null) {
            return null;
        }
        NodeDTO dto = new NodeDTO();
        dto.setHost(node.host());
        dto.setPort(node.port());
        dto.setProtocol(node.protocol().value);
        return dto;
    }

    public static Node toNode(NodeDTO dto) {
        if (dto == null) {
            return null;
        }
        return new BaseNode(Protocol.parse(dto.getProtocol()), dto.getHost(), dto.getPort());
    }

    public static List<NodeDTO> toBrokerNodeDTO(List<BrokerNode> nodes) {
        if (CollectionUtils.isEmpty(nodes)) {
            return Collections.emptyList();
        }
        return nodes.stream().map(BrokerClientConverter::toDTO).collect(Collectors.toList());
    }

    public static List<NodeDTO> toNodes(List<Worker> workers) {
        if (CollectionUtils.isEmpty(workers)) {
            return Collections.emptyList();
        }
        return workers.stream().map(w -> {
            NodeDTO dto = new NodeDTO();
            dto.setHost(w.getHost());
            dto.setPort(w.getPort());
            return dto;
        }).collect(Collectors.toList());
    }

    public static TaskMonitor convert(TaskMonitorDTO dto) {
        if (dto == null) {
            return null;
        }
        return new TaskMonitor(dto.getTotalNum(), dto.getSuccessNum(), dto.getFailNum());
    }

}
