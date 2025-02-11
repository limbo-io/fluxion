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
import io.fluxion.remote.core.api.dto.BrokerDTO;
import io.fluxion.remote.core.api.dto.BrokerTopologyDTO;
import io.fluxion.remote.core.api.dto.SystemInfoDTO;
import io.fluxion.remote.core.api.dto.WorkerTagDTO;
import io.fluxion.remote.core.api.request.WorkerRegisterRequest;
import io.fluxion.remote.core.constants.Protocol;
import io.fluxion.server.core.cluster.Node;
import io.fluxion.server.core.worker.Worker;
import io.fluxion.server.core.worker.executor.WorkerExecutor;
import io.fluxion.server.core.worker.metric.WorkerMetric;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;

/**
 * @author Devil
 */
public class BrokerClientConverter {

    public static Worker toWorker(String appId, WorkerRegisterRequest request) {
        Worker worker = new Worker(appId, request.getHost(), request.getPort(), Protocol.parse(request.getProtocol()));

        WorkerMetric metric = toMetric(request.getSystemInfo(), request.getAvailableQueueNum());
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

    public static WorkerMetric toMetric(SystemInfoDTO systemInfoDTO, int availableQueueNum) {
        return new WorkerMetric(
            systemInfoDTO.getCpuProcessors(),
            systemInfoDTO.getCpuLoad(),
            systemInfoDTO.getFreeMemory(),
            availableQueueNum,
            TimeUtils.currentLocalDateTime()
        );
    }

    public static BrokerTopologyDTO toBrokerTopologyDTO(Collection<Node> nodes) {
        BrokerTopologyDTO brokerTopologyDTO = new BrokerTopologyDTO();
        if (CollectionUtils.isNotEmpty(nodes)) {
            for (Node node : nodes) {
                BrokerDTO dto = new BrokerDTO();
                dto.setId(node.id());
                dto.setProtocols(toProtocolsDTO(node.protocols()));
                brokerTopologyDTO.getBrokers().add(dto);
            }
        }
        return brokerTopologyDTO;
    }

    public static Map<String, List<BrokerDTO.Address>> toProtocolsDTO(Map<Protocol, List<Node.Address>> protocols) {
        if (MapUtils.isEmpty(protocols)) {
            return Collections.emptyMap();
        }
        Map<String, List<BrokerDTO.Address>> result = new HashMap<>();
        for (Map.Entry<Protocol, List<Node.Address>> entry : protocols.entrySet()) {
            String protocol = entry.getKey().getValue();
            List<Node.Address> addresses = entry.getValue();
            result.put(protocol, addresses.stream().map(add -> {
                BrokerDTO.Address dto = new BrokerDTO.Address();
                dto.setHost(add.getHost());
                dto.setPort(add.getPort());
                return dto;
            }).collect(Collectors.toList()));
        }
        return result;
    }

}
