/*
 * Copyright 2025-2030 Limbo Team (https://github.com/limbo-io).
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

package io.fluxion.worker.core.discovery;

import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.remote.core.api.dto.BrokerTopologyDTO;
import io.fluxion.remote.core.api.dto.NodeDTO;
import io.fluxion.remote.core.api.dto.SystemInfoDTO;
import io.fluxion.remote.core.api.dto.WorkerExecutorDTO;
import io.fluxion.remote.core.api.dto.TagDTO;
import io.fluxion.remote.core.api.request.broker.WorkerHeartbeatRequest;
import io.fluxion.remote.core.api.request.broker.WorkerRegisterRequest;
import io.fluxion.remote.core.api.response.broker.WorkerHeartbeatResponse;
import io.fluxion.remote.core.api.response.broker.WorkerRegisterResponse;
import io.fluxion.remote.core.client.Client;
import io.fluxion.remote.core.client.ClientFactory;
import io.fluxion.remote.core.client.LBClient;
import io.fluxion.remote.core.client.RetryableClient;
import io.fluxion.remote.core.cluster.BaseNode;
import io.fluxion.remote.core.cluster.Node;
import io.fluxion.remote.core.constants.Protocol;
import io.fluxion.remote.core.exception.RpcException;
import io.fluxion.remote.core.heartbeat.Heartbeat;
import io.fluxion.remote.core.heartbeat.HeartbeatPacemaker;
import io.fluxion.remote.core.lb.BaseLBServer;
import io.fluxion.remote.core.lb.LBServer;
import io.fluxion.remote.core.lb.repository.LBServerRepository;
import io.fluxion.worker.core.SystemInfo;
import io.fluxion.worker.core.WorkerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.fluxion.remote.core.constants.BrokerRemoteConstant.API_WORKER_HEARTBEAT;
import static io.fluxion.remote.core.constants.BrokerRemoteConstant.API_WORKER_REGISTER;
import static io.fluxion.remote.core.constants.WorkerRemoteConstant.HEARTBEAT_TIMEOUT_SECOND;

/**
 * 服务注册发现
 * 用于和broker建立连接管理broker信息
 *
 *
 * @author PengQ
 * @since 0.0.1
 */
public class DefaultServerDiscovery implements ServerDiscovery {

    private static final Logger log = LoggerFactory.getLogger(DefaultServerDiscovery.class);

    private final LBClient client;

    private final LBServerRepository repository;

    private final WorkerContext workerContext;

    private String topologyVersion;

    /**
     * manage heartbeat
     */
    private final HeartbeatPacemaker heartbeatPacemaker;

    private Node heartbeatBroker;

    public DefaultServerDiscovery(WorkerContext workerContext, LBClient client, LBServerRepository repository) {
        this.repository = repository;
        this.workerContext = workerContext;
        this.client = client;
        this.heartbeatPacemaker = new HeartbeatPacemaker(new WorkerHeartbeat(), Duration.ofSeconds(HEARTBEAT_TIMEOUT_SECOND));
    }

    @Override
    public void start() {
        register();
        this.heartbeatPacemaker.start();
    }

    private void register() {
        BaseNode node = workerContext.node();
        WorkerRegisterRequest request = new WorkerRegisterRequest();
        request.setProtocol(node.protocol().value);
        request.setHost(node.host());
        request.setPort(node.port());
        request.setAppName(workerContext.appName());
        request.setSystemInfo(systemInfoDTO());
        request.setTags(tagDTOS(workerContext.tags()));
        request.setExecutors(executorDTOS(workerContext));
        request.setAvailableQueueNum(workerContext.availableQueueNum());

        WorkerRegisterResponse registerResponse = client.call(API_WORKER_REGISTER, request).getData();
        workerContext.appId(registerResponse.getAppId());
        repository.updateServers(brokers(registerResponse.getBrokerTopology()));
        topologyVersion = registerResponse.getBrokerTopology().getVersion();
        heartbeatBroker = node(registerResponse.getBroker());
    }

    class WorkerHeartbeat implements Heartbeat {

        private final Client client;

        public WorkerHeartbeat() {
            this.client = RetryableClient.builder()
                .client(ClientFactory.create(workerContext.node().protocol()))
                .build();
        }

        @Override
        public void beat() {
            try {
                String version = topologyVersion;
                if (repository.listAliveServers().isEmpty()) {
                    version = null;
                }
                WorkerHeartbeatRequest request = new WorkerHeartbeatRequest();
                request.setAppId(workerContext.appId());
                request.setWorkerId(workerContext.node().address());
                request.setSystemInfo(systemInfoDTO());
                request.setTopologyVersion(version);
                request.setHeartbeatAt(TimeUtils.currentLocalDateTime());
                request.setAvailableQueueNum(workerContext.availableQueueNum());

                WorkerHeartbeatResponse heartbeatResponse = client.call(
                    API_WORKER_HEARTBEAT, heartbeatBroker.host(), heartbeatBroker.port(), request
                ).getData();
                BrokerTopologyDTO brokerTopology = heartbeatResponse.getBrokerTopology();
                if (!brokerTopology.getVersion().equals(version)) {
                    repository.updateServers(brokers(brokerTopology));
                    topologyVersion = brokerTopology.getVersion();
                }
            } catch (RpcException e) {
                log.warn("[DefaultServerDiscovery] send heartbeat failed e:{}", e.getMessage());
                // 换新broker节点重新注册
                register();
            }
        }
    }

    private List<WorkerExecutorDTO> executorDTOS(WorkerContext workerContext) {
        return workerContext.executors().stream().map(
            e -> {
                WorkerExecutorDTO dto = new WorkerExecutorDTO();
                dto.setName(e.name());
                return dto;
            }
        ).collect(Collectors.toList());
    }

    private Node node(NodeDTO dto) {
        return new BaseNode(Protocol.parse(dto.getProtocol()), dto.getHost(), dto.getPort());
    }

    private SystemInfoDTO systemInfoDTO() {
        SystemInfoDTO dto = new SystemInfoDTO();
        dto.setCpuLoad(SystemInfo.cpuLoad());
        dto.setFreeMemory(SystemInfo.freeMemory());
        dto.setCpuProcessors(SystemInfo.cpuProcessors());
        return dto;
    }

    private List<LBServer> brokers(BrokerTopologyDTO topologyDTO) {
        if (topologyDTO == null || CollectionUtils.isEmpty(topologyDTO.getBrokers())) {
            return Collections.emptyList();
        }
        return topologyDTO.getBrokers().stream().map(dto -> {
            Node node = node(dto);
            return new BaseLBServer(node);
        }).collect(Collectors.toList());
    }

    private List<TagDTO> tagDTOS(Map<String, Set<String>> tags) {
        return tags.keySet().stream()
            .flatMap(key -> tags.get(key)
                .stream().map(value -> new TagDTO(key, value))
            )
            .collect(Collectors.toList());
    }

    @Override
    public void stop() {
        heartbeatPacemaker.stop();
    }

}
