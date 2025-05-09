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

package io.fluxion.worker.core.remote;

import io.fluxion.remote.core.api.dto.NodeDTO;
import io.fluxion.remote.core.api.request.worker.JobDispatchRequest;
import io.fluxion.remote.core.api.request.worker.TaskDispatchRequest;
import io.fluxion.remote.core.cluster.BaseNode;
import io.fluxion.remote.core.cluster.Node;
import io.fluxion.remote.core.constants.ExecuteMode;
import io.fluxion.remote.core.constants.Protocol;
import io.fluxion.remote.core.constants.TaskStatus;
import io.fluxion.worker.core.WorkerContext;
import io.fluxion.worker.core.job.Job;
import io.fluxion.worker.core.task.Task;

/**
 * @author PengQ
 * @since 0.0.1
 */
public class WorkerClientConverter {

    public static Job toJob(JobDispatchRequest request) {
        Job job = new Job();
        job.setId(request.getJobId());
        job.setExecutorName(request.getExecutorName());
        job.setExecuteMode(ExecuteMode.parse(request.getExecuteMode()));
        return job;
    }

    public static Task toTask(TaskDispatchRequest request, WorkerContext workerContext) {
        Task task = new Task(request.getTaskId(), request.getJobId());
        task.setStatus(TaskStatus.INITED);
        task.setWorkerNode(workerContext.node());
        task.setRemoteNode(toNode(request.getRemoteNode()));
        return task;
    }

    public static Node toNode(NodeDTO dto) {
        if (dto == null) {
            return null;
        }
        return new BaseNode(Protocol.parse(dto.getProtocol()), dto.getHost(), dto.getPort());
    }

    public static NodeDTO toDTO(Node node) {
        NodeDTO dto = new NodeDTO();
        dto.setPort(node.port());
        dto.setHost(node.host());
        dto.setProtocol(node.protocol().value);
        return dto;
    }

}
