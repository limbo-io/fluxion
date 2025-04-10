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
import io.fluxion.remote.core.api.dto.BrokerTopologyDTO;
import io.fluxion.remote.core.api.dto.NodeDTO;
import io.fluxion.remote.core.api.request.JobDispatchedRequest;
import io.fluxion.remote.core.api.request.JobFailRequest;
import io.fluxion.remote.core.api.request.JobReportRequest;
import io.fluxion.remote.core.api.request.JobStartRequest;
import io.fluxion.remote.core.api.request.JobSuccessRequest;
import io.fluxion.remote.core.api.request.JobWorkersRequest;
import io.fluxion.remote.core.api.request.WorkerHeartbeatRequest;
import io.fluxion.remote.core.api.request.WorkerRegisterRequest;
import io.fluxion.remote.core.api.response.JobWorkersResponse;
import io.fluxion.remote.core.api.response.WorkerHeartbeatResponse;
import io.fluxion.remote.core.api.response.WorkerRegisterResponse;
import io.fluxion.remote.core.client.server.ClientHandler;
import io.fluxion.remote.core.constants.BrokerRemoteConstant;
import io.fluxion.server.core.app.cmd.AppSaveCmd;
import io.fluxion.server.core.broker.converter.BrokerClientConverter;
import io.fluxion.server.core.broker.query.BrokersQuery;
import io.fluxion.server.core.execution.cmd.ExecutableFailCmd;
import io.fluxion.server.core.execution.cmd.ExecutableSuccessCmd;
import io.fluxion.server.core.job.ExecutorJob;
import io.fluxion.server.core.job.Job;
import io.fluxion.server.core.job.cmd.JobDispatchedCmd;
import io.fluxion.server.core.job.cmd.JobReportCmd;
import io.fluxion.server.core.job.cmd.JobStartCmd;
import io.fluxion.server.core.job.query.JobByIdQuery;
import io.fluxion.server.core.worker.Worker;
import io.fluxion.server.core.worker.cmd.WorkerHeartbeatCmd;
import io.fluxion.server.core.worker.cmd.WorkerSaveCmd;
import io.fluxion.server.core.worker.query.WorkersFilterQuery;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Query;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * @author Devil
 */
@Slf4j
public class BrokerClientHandler implements ClientHandler {

    @Override
    public Response<?> process(String path, String data) {
        try {
            switch (path) {
                case BrokerRemoteConstant.API_WORKER_REGISTER: {
                    return Response.ok(register(data));
                }
                case BrokerRemoteConstant.API_WORKER_HEARTBEAT: {
                    return Response.ok(heartbeat(data));
                }
                case BrokerRemoteConstant.API_BROKER_PING: {
                    return Response.ok(true);
                }
                case BrokerRemoteConstant.API_JOB_DISPATCHED: {
                    return Response.ok(jobDispatched(data));
                }
                case BrokerRemoteConstant.API_JOB_START: {
                    return Response.ok(jobStart(data));
                }
                case BrokerRemoteConstant.API_JOB_REPORT: {
                    return Response.ok(jobReport(data));
                }
                case BrokerRemoteConstant.API_JOB_SUCCESS: {
                    return Response.ok(jobSuccess(data));
                }
                case BrokerRemoteConstant.API_JOB_FAIL: {
                    return Response.ok(jobFail(data));
                }
                case BrokerRemoteConstant.API_JOB_WORKERS: {
                    return Response.ok(jobWorkers(data));
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

    private WorkerRegisterResponse register(String data) {
        WorkerRegisterRequest request = JacksonUtils.toType(data, WorkerRegisterRequest.class);
        // 注册app
        String appId = Cmd.send(new AppSaveCmd(request.getAppName())).getAppId();
        String workerId = Cmd.send(new WorkerSaveCmd(
            BrokerClientConverter.toWorker(appId, request)
        )).getWorkerId();
        BrokersQuery.Response brokersRes = Query.query(new BrokersQuery());
        BrokerTopologyDTO brokerTopologyDTO = new BrokerTopologyDTO();
        brokerTopologyDTO.setVersion(brokersRes.getVersion());
        brokerTopologyDTO.setBrokers(BrokerClientConverter.toBrokerNodeDTO(brokersRes.getBrokerNodes()));
        NodeDTO brokerDTO = BrokerClientConverter.toDTO(BrokerContext.broker().node());
        WorkerRegisterResponse response = new WorkerRegisterResponse();
        response.setAppId(appId);
        response.setWorkerId(workerId);
        response.setBrokerTopology(brokerTopologyDTO);
        response.setBroker(brokerDTO);
        return response;
    }

    private WorkerHeartbeatResponse heartbeat(String data) {
        WorkerHeartbeatRequest request = JacksonUtils.toType(data, WorkerHeartbeatRequest.class);
        // 心跳
        Cmd.send(new WorkerHeartbeatCmd(
            request.getWorkerId(),
            BrokerClientConverter.toMetric(request.getSystemInfo(), request.getAvailableQueueNum(), request.getHeartbeatAt())
        ));
        BrokersQuery.Response brokersRes = Query.query(new BrokersQuery());
        BrokerTopologyDTO brokerTopologyDTO = new BrokerTopologyDTO();
        brokerTopologyDTO.setVersion(brokersRes.getVersion());
        if (!brokersRes.getVersion().equals(request.getTopologyVersion())) {
            brokerTopologyDTO.setBrokers(BrokerClientConverter.toBrokerNodeDTO(brokersRes.getBrokerNodes()));
        }
        WorkerHeartbeatResponse response = new WorkerHeartbeatResponse();
        // 不需要每次心跳返回所有节点信息
        response.setBrokerTopology(brokerTopologyDTO);
        return response;
    }

    private boolean jobDispatched(String data) {
        JobDispatchedRequest request = JacksonUtils.toType(data, JobDispatchedRequest.class);
        return Cmd.send(new JobDispatchedCmd(
            request.getJobId(), request.getWorkerAddress()
        ));
    }

    private boolean jobStart(String data) {
        JobStartRequest request = JacksonUtils.toType(data, JobStartRequest.class);
        return Cmd.send(new JobStartCmd(
            request.getJobId(), request.getReportAt(), request.getWorkerAddress()
        ));
    }

    private boolean jobReport(String data) {
        JobReportRequest request = JacksonUtils.toType(data, JobReportRequest.class);
        return Cmd.send(new JobReportCmd(
            request.getJobId(), request.getWorkerAddress(),
            request.getReportAt(), BrokerClientConverter.convert(request.getTaskMonitor())
        ));
    }

    private boolean jobSuccess(String data) {
        JobSuccessRequest request = JacksonUtils.toType(data, JobSuccessRequest.class);
        return Cmd.send(new ExecutableSuccessCmd(
            request.getJobId(), request.getReportAt(),
            BrokerClientConverter.convert(request.getTaskMonitor())
        ));
    }

    private boolean jobFail(String data) {
        JobFailRequest request = JacksonUtils.toType(data, JobFailRequest.class);
        return Cmd.send(new ExecutableFailCmd(
            request.getJobId(), request.getReportAt(), request.getErrorMsg(),
            BrokerClientConverter.convert(request.getTaskMonitor())
        ));
    }

    private JobWorkersResponse jobWorkers(String data) {
        JobWorkersRequest request = JacksonUtils.toType(data, JobWorkersRequest.class);
        Job job = Query.query(new JobByIdQuery(request.getJobId())).getJob();
        if (!(job instanceof ExecutorJob)) {
            return new JobWorkersResponse();
        }
        ExecutorJob executorJob = (ExecutorJob) job;
        List<Worker> workers = Query.query(new WorkersFilterQuery(
            executorJob.getAppId(), executorJob.getExecutorName(),
            executorJob.getDispatchOption(), request.isFilterResource(), request.isLoadBalanceSelect()
        )).getWorkers();
        JobWorkersResponse response = new JobWorkersResponse();
        response.setWorkers(BrokerClientConverter.toNodes(workers));
        return response;
    }

}
