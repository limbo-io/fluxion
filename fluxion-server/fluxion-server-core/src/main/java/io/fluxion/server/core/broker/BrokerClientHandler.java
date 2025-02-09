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

package io.fluxion.server.core.broker;

import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.remote.core.api.Response;
import io.fluxion.remote.core.api.request.WorkerHeartbeatRequest;
import io.fluxion.remote.core.api.request.WorkerRegisterRequest;
import io.fluxion.remote.core.api.response.WorkerHeartbeatResponse;
import io.fluxion.remote.core.api.response.WorkerRegisterResponse;
import io.fluxion.remote.core.client.server.ClientHandler;
import io.fluxion.remote.core.constants.BrokerConstant;
import io.fluxion.server.core.app.cmd.AppRegisterCmd;
import io.fluxion.server.core.broker.converter.BrokerRpcConverter;
import io.fluxion.server.core.cluster.NodeManger;
import io.fluxion.server.core.worker.cmd.WorkerHeartbeatCmd;
import io.fluxion.server.core.worker.cmd.WorkerRegisterCmd;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author Devil
 */
@Slf4j
@Component
public class BrokerClientHandler implements ClientHandler {

    @Resource
    private NodeManger nodeManger;

    @Override
    public Response<?> process(String path, String data) {
        switch (path) {
            case BrokerConstant.API_WORKER_REGISTER: {
                WorkerRegisterRequest request = JacksonUtils.toType(data, WorkerRegisterRequest.class);
                // 注册app
                String appId = Cmd.send(new AppRegisterCmd(request.getAppName())).getId();
                // 注册worker
                String workerId = Cmd.send(new WorkerRegisterCmd(
                    BrokerRpcConverter.toWorker(appId, request)
                )).getId();
                WorkerRegisterResponse response = new WorkerRegisterResponse();
                response.setWorkerId(workerId);
                response.setBrokerTopology(BrokerRpcConverter.toBrokerTopologyDTO(nodeManger.allAlive()));
                return Response.ok(response);
            }
            case BrokerConstant.API_WORKER_HEARTBEAT: {
                WorkerHeartbeatRequest request = JacksonUtils.toType(data, WorkerHeartbeatRequest.class);
                Cmd.send(new WorkerHeartbeatCmd(
                    request.getWorkerId(),
                    BrokerRpcConverter.toMetric(request.getSystemInfo(), request.getAvailableQueueNum())
                ));
                WorkerHeartbeatResponse response = new WorkerHeartbeatResponse();
                response.setBrokerTopology(BrokerRpcConverter.toBrokerTopologyDTO(nodeManger.allAlive()));
                return Response.ok(response);
            }
        }
        String msg = "Invalid request, Path NotFound.";
        log.info("{} path={}", msg, path);
        return Response.builder().notFound(msg).build();
    }

}
