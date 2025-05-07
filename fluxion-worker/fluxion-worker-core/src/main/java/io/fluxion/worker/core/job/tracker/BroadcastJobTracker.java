/*
 * Copyright 2025-2030 limbo-io Team (https://github.com/limbo-io).
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

package io.fluxion.worker.core.job.tracker;

import io.fluxion.remote.core.api.Response;
import io.fluxion.remote.core.api.dto.NodeDTO;
import io.fluxion.remote.core.api.request.broker.JobWorkersRequest;
import io.fluxion.remote.core.api.response.broker.JobWorkersResponse;
import io.fluxion.remote.core.cluster.Node;
import io.fluxion.remote.core.constants.TaskStatus;
import io.fluxion.worker.core.WorkerContext;
import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.job.Job;
import io.fluxion.worker.core.remote.WorkerClientConverter;
import io.fluxion.worker.core.task.Task;
import io.fluxion.worker.core.task.repository.TaskRepository;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

import static io.fluxion.remote.core.constants.BrokerRemoteConstant.API_JOB_WORKERS;

/**
 * @author Devil
 */
public class BroadcastJobTracker extends DistributedJobTracker {

    public BroadcastJobTracker(Job job, Executor executor, WorkerContext workerContext, TaskRepository taskRepository) {
        super(job, executor, workerContext, taskRepository);
    }

    @Override
    public void run() {
        // 获取所有节点，创建task
        JobWorkersRequest request = new JobWorkersRequest();
        request.setJobId(job.getId());
        Response<JobWorkersResponse> workerResponse = workerContext.call(API_JOB_WORKERS, request);
        if (!workerResponse.success()) {
            jobFail("Get Workers Fail");
            return;
        }

        List<NodeDTO> workers = workerResponse.getData().getWorkers();
        if (CollectionUtils.isEmpty(workers)) {
            jobSuccess("");
        } else {
            List<Task> tasks = new ArrayList<>();
            for (int i = 0; i < workers.size(); i++) {
                NodeDTO worker = workers.get(i);
                Task task = new Task("SUB_" + i, job.getId());
                task.setStatus(TaskStatus.INITED);
                task.setRemoteNode(workerContext.node());
                task.setWorkerNode(WorkerClientConverter.toNode(worker));
                tasks.add(task);
            }

            // 保存
            createTasks(tasks);

            // 下发
            dispatch(tasks);
        }
    }

    @Override
    public void success() {
        if (!taskCounter.isFinished()) {
            return;
        }
        jobSuccess("");
    }

    @Override
    public void fail() {
        if (!taskCounter.isFinished()) {
            return;
        }
        // 后面应该根据策略，现在是只要一个成功就是成功
        if (taskCounter.success() > 0) {
            jobSuccess("");
        } else {
            jobFail("All Task Execute Fail");
        }
    }

    @Override
    protected Node findWorker(Task task) {
        return task.getWorkerNode() == null ? null : task.getWorkerNode();
    }

}
