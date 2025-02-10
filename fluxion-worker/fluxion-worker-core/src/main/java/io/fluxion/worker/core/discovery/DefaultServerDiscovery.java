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

package io.fluxion.worker.core.discovery;

import io.fluxion.remote.core.api.dto.SystemInfoDTO;
import io.fluxion.remote.core.api.dto.WorkerExecutorDTO;
import io.fluxion.remote.core.api.dto.WorkerTagDTO;
import io.fluxion.remote.core.api.request.WorkerHeartbeatRequest;
import io.fluxion.remote.core.api.request.WorkerRegisterRequest;
import io.fluxion.remote.core.client.LBClient;
import io.fluxion.remote.core.exception.RpcException;
import io.fluxion.remote.core.heartbeat.HeartbeatPacemaker;
import io.fluxion.worker.core.SystemInfo;
import io.fluxion.worker.core.WorkerInfo;
import io.fluxion.worker.core.remote.BrokerSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.fluxion.remote.core.constants.BrokerConstant.API_WORKER_HEARTBEAT;
import static io.fluxion.remote.core.constants.BrokerConstant.API_WORKER_REGISTER;
import static io.fluxion.remote.core.constants.WorkerConstant.HEARTBEAT_TIMEOUT_SECOND;

/**
 * @author PengQ
 * @since 0.0.1
 */
public class DefaultServerDiscovery implements ServerDiscovery {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final LBClient client;

    private final WorkerInfo workerInfo;

    /**
     * manage heartbeat
     */
    private HeartbeatPacemaker heartbeatPacemaker;

    public DefaultServerDiscovery(WorkerInfo workerInfo, LBClient client) {
        this.client = client;
        this.workerInfo = workerInfo;
    }

    @Override
    public void start() {
        // 注册
        BrokerSender.register(client, API_WORKER_REGISTER, registerRequest(workerInfo));
        // 心跳管理
        this.heartbeatPacemaker = new HeartbeatPacemaker(() -> {
            try {
                BrokerSender.heartbeat(client, API_WORKER_HEARTBEAT, heartbeatRequest(workerInfo));
            } catch (RpcException e) {
                log.warn("[DefaultServerDiscovery] send heartbeat failed");
                throw new IllegalStateException("[DefaultServerDiscovery] send heartbeat failed", e);
            }
        }, Duration.ofSeconds(HEARTBEAT_TIMEOUT_SECOND));
    }

    private WorkerRegisterRequest registerRequest(WorkerInfo workerInfo) {
        WorkerRegisterRequest request = new WorkerRegisterRequest();
        request.setAppName(workerInfo.getAppName());
        request.setSystemInfo(systemInfoDTO());
        request.setTags(tagDTOS(workerInfo.getTags()));
        request.setExecutors(executorDTOS(workerInfo));
        return request;
    }

    private WorkerHeartbeatRequest heartbeatRequest(WorkerInfo workerInfo) {
        WorkerHeartbeatRequest request = new WorkerHeartbeatRequest();
        request.setWorkerId(workerInfo.getWorkerId());
        request.setSystemInfo(systemInfoDTO());
        return request;
    }

    private List<WorkerExecutorDTO> executorDTOS(WorkerInfo workerInfo) {
        return workerInfo.getExecutors().stream().map(
            e -> {
                WorkerExecutorDTO dto = new WorkerExecutorDTO();
                dto.setName(e.name());
                return dto;
            }
        ).collect(Collectors.toList());
    }

    private SystemInfoDTO systemInfoDTO() {
        SystemInfoDTO dto = new SystemInfoDTO();
        dto.setCpuLoad(SystemInfo.cpuLoad());
        dto.setFreeMemory(SystemInfo.freeMemory());
        dto.setCpuProcessors(SystemInfo.cpuProcessors());
        return dto;
    }

    private List<WorkerTagDTO> tagDTOS(Map<String, Set<String>> tags) {
        return tags.keySet().stream()
            .flatMap(key -> tags.get(key)
                .stream().map(value -> new WorkerTagDTO(key, value))
            )
            .collect(Collectors.toList());
    }

    @Override
    public void stop() {
        heartbeatPacemaker.stop();
    }
}
