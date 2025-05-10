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
import io.fluxion.remote.core.api.request.broker.JobReportRequest;
import io.fluxion.remote.core.api.request.broker.JobStateTransitionRequest;
import io.fluxion.remote.core.api.request.broker.JobWorkersRequest;
import io.fluxion.remote.core.api.request.broker.WorkerHeartbeatRequest;
import io.fluxion.remote.core.api.request.broker.WorkerRegisterRequest;
import io.fluxion.remote.core.api.response.broker.JobReportResponse;
import io.fluxion.remote.core.api.response.broker.JobStateTransitionResponse;
import io.fluxion.remote.core.api.response.broker.JobWorkersResponse;
import io.fluxion.remote.core.api.response.broker.WorkerHeartbeatResponse;
import io.fluxion.remote.core.api.response.broker.WorkerRegisterResponse;
import io.fluxion.remote.core.client.server.ClientHandler;
import io.fluxion.remote.core.constants.BrokerRemoteConstant;
import io.fluxion.remote.core.constants.JobStateEvent;
import io.fluxion.remote.core.constants.JobStatus;
import io.fluxion.server.core.app.cmd.AppSaveCmd;
import io.fluxion.server.core.broker.converter.BrokerClientConverter;
import io.fluxion.server.core.broker.query.BrokersQuery;
import io.fluxion.server.core.job.Job;
import io.fluxion.server.core.job.cmd.JobReportCmd;
import io.fluxion.server.core.job.cmd.JobStateTransitionCmd;
import io.fluxion.server.core.job.config.ExecutorJobConfig;
import io.fluxion.server.core.job.query.JobByIdQuery;
import io.fluxion.server.core.job.query.JobConfigQuery;
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
                case BrokerRemoteConstant.API_JOB_REPORT: {
                    return Response.ok(jobReport(data));
                }
                case BrokerRemoteConstant.API_JOB_STATE_TRANSITION: {
                    return Response.ok(jobStateTransition(data));
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

    private JobReportResponse jobReport(String data) {
        JobReportRequest request = JacksonUtils.toType(data, JobReportRequest.class);
        JobReportCmd.Response response = Cmd.send(new JobReportCmd(
            request.getJobId(), BrokerClientConverter.toNode(request.getWorkerNode()),
            request.getReportAt(), BrokerClientConverter.convert(request.getMonitor()),
            JobStatus.parse(request.getStatus())
        ));
        JobReportResponse res = new JobReportResponse();
        res.setSuccess(response.isSuccess());
        return res;
    }

    private JobStateTransitionResponse jobStateTransition(String data) {
        JobStateTransitionRequest request = JacksonUtils.toType(data, JobStateTransitionRequest.class);
        JobStateTransitionCmd.Response response = Cmd.send(new JobStateTransitionCmd(
            request.getJobId(), BrokerClientConverter.toNode(request.getWorkerNode()),
            request.getReportAt(), BrokerClientConverter.convert(request.getMonitor()),
            JobStateEvent.parse(request.getEvent()), request.getResult(), request.getErrorMsg()
        ));
        JobStateTransitionResponse res = new JobStateTransitionResponse();
        res.setSuccess(response.isSuccess());
        return res;
    }

    private JobWorkersResponse jobWorkers(String data) {
        JobWorkersRequest request = JacksonUtils.toType(data, JobWorkersRequest.class);
        Job job = Query.query(new JobByIdQuery(request.getJobId())).getJob();
        Job.Config config = Query.query(new JobConfigQuery(job.getExecutionId(), job.getRefId())).getConfig();
        if (!(config instanceof ExecutorJobConfig)) {
            return new JobWorkersResponse();
        }
        ExecutorJobConfig executorJobConfig = (ExecutorJobConfig) config;
        List<Worker> workers = Query.query(new WorkersFilterQuery(
            executorJobConfig.getAppId(), executorJobConfig.getExecutorName(),
            executorJobConfig.getDispatchOption(), request.isFilterResource(), request.isLoadBalanceSelect()
        )).getWorkers();
        JobWorkersResponse response = new JobWorkersResponse();
        response.setWorkers(BrokerClientConverter.toNodes(workers));
        return response;
    }

}
