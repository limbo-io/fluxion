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

package io.fluxion.server.core.broker;

import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.remote.core.api.Response;
import io.fluxion.remote.core.api.request.broker.TaskFailRequest;
import io.fluxion.remote.core.api.request.broker.TaskReportRequest;
import io.fluxion.remote.core.api.request.broker.TaskStartRequest;
import io.fluxion.remote.core.api.request.broker.TaskSuccessRequest;
import io.fluxion.remote.core.api.request.broker.WorkerHeartbeatRequest;
import io.fluxion.remote.core.api.request.broker.WorkerRegisterRequest;
import io.fluxion.remote.core.api.response.broker.WorkerHeartbeatResponse;
import io.fluxion.remote.core.api.response.broker.WorkerRegisterResponse;
import io.fluxion.remote.core.client.server.ClientHandler;
import io.fluxion.remote.core.constants.BrokerConstant;
import io.fluxion.server.core.app.App;
import io.fluxion.server.core.app.cmd.AppBrokerElectCmd;
import io.fluxion.server.core.app.cmd.AppRegisterCmd;
import io.fluxion.server.core.broker.converter.BrokerClientConverter;
import io.fluxion.server.core.task.cmd.TaskFailCmd;
import io.fluxion.server.core.task.cmd.TaskReportCmd;
import io.fluxion.server.core.task.cmd.TaskStartCmd;
import io.fluxion.server.core.task.cmd.TaskSuccessCmd;
import io.fluxion.server.core.worker.cmd.WorkerHeartbeatCmd;
import io.fluxion.server.core.worker.cmd.WorkerRegisterCmd;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Devil
 */
@Slf4j
public class BrokerClientHandler implements ClientHandler {

    @Override
    public Response<?> process(String path, String data) {
        try {
            switch (path) {
                case BrokerConstant.API_WORKER_REGISTER: {
                    return Response.ok(register(JacksonUtils.toType(data, WorkerRegisterRequest.class)));
                }
                case BrokerConstant.API_WORKER_HEARTBEAT: {
                    return Response.ok(heartbeat(JacksonUtils.toType(data, WorkerHeartbeatRequest.class)));
                }
                case BrokerConstant.API_BROKER_PING: {
                    return Response.ok(Response.ok(true));
                }
                case BrokerConstant.API_TASK_START: {
                    TaskStartRequest request = JacksonUtils.toType(data, TaskStartRequest.class);
                    Boolean success = Cmd.send(new TaskStartCmd(request.getTaskId(), request.getWorkerAddress()));
                    return Response.ok(Response.ok(success));
                }
                case BrokerConstant.API_TASK_REPORT: {
                    TaskReportRequest request = JacksonUtils.toType(data, TaskReportRequest.class);
                    Boolean success = Cmd.send(new TaskReportCmd(
                        request.getTaskId(), request.getWorkerAddress(), request.getReportTime()
                    ));
                    return Response.ok(Response.ok(success));
                }
                case BrokerConstant.API_TASK_SUCCESS: {
                    TaskSuccessRequest request = JacksonUtils.toType(data, TaskSuccessRequest.class);
                    Boolean success = Cmd.send(new TaskSuccessCmd(
                        request.getTaskId(), request.getWorkerAddress(), request.getReportTime()
                    ));
                    return Response.ok(Response.ok(success));
                }
                case BrokerConstant.API_TASK_FAIL: {
                    TaskFailRequest request = JacksonUtils.toType(data, TaskFailRequest.class);
                    Boolean success = Cmd.send(new TaskFailCmd(
                        request.getTaskId(), request.getWorkerAddress(), request.getReportTime(),
                        request.getErrorMsg()
                    ));
                    return Response.ok(Response.ok(success));
                }
            }
            String msg = "Invalid request, Path NotFound.";
            log.info("{} path={}", msg, path);
            return Response.builder().notFound(msg).build();
        } catch (Exception e) {
            log.error("Request process error path={} data={}", path, data, e);
            return Response.builder().error(e.getMessage()).build();
        }
    }

    private WorkerRegisterResponse register(WorkerRegisterRequest request) {
        // 注册app
        App app = Cmd.send(new AppRegisterCmd(request.getAppName())).getApp();
        // 注册worker
        String workerId = Cmd.send(new WorkerRegisterCmd(
            BrokerClientConverter.toWorker(app.getId(), request)
        )).getWorkerId();
        WorkerRegisterResponse response = new WorkerRegisterResponse();
        response.setAppId(app.getId());
        response.setWorkerId(workerId);
        response.setBroker(BrokerClientConverter.toDTO(app.getBroker()));
        response.setBrokerTopology(BrokerClientConverter.toBrokerTopologyDTO(app.getBrokers()));
        return response;
    }

    private WorkerHeartbeatResponse heartbeat(WorkerHeartbeatRequest request) {
        // 尝试选举
        AppBrokerElectCmd.Response brokerElect = Cmd.send(new AppBrokerElectCmd());
        // 心跳
        Cmd.send(new WorkerHeartbeatCmd(
            request.getWorkerId(),
            BrokerClientConverter.toMetric(request.getSystemInfo(), request.getAvailableQueueNum(), request.getHeartbeatTime())
        ));
        WorkerHeartbeatResponse response = new WorkerHeartbeatResponse();
        response.setBroker(BrokerClientConverter.toDTO(brokerElect.getBroker()));
        response.setElected(brokerElect.isElected());
        // 不需要每次心跳返回所有节点信息
        if (brokerElect.isElected()) {
            response.setBrokerTopology(BrokerClientConverter.toBrokerTopologyDTO(null));
        } else {
            response.setBrokerTopology(BrokerClientConverter.toBrokerTopologyDTO(brokerElect.getBrokers()));
        }
        return response;
    }

}
