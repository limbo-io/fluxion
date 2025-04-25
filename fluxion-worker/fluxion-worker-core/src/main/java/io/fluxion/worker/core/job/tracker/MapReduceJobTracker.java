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
import io.fluxion.worker.core.executor.MapExecutor;
import io.fluxion.worker.core.executor.MapReduceExecutor;
import io.fluxion.worker.core.job.Job;
import io.fluxion.worker.core.job.JobContext;
import io.fluxion.worker.core.remote.WorkerClientConverter;
import io.fluxion.worker.core.task.Task;
import io.fluxion.worker.core.task.repository.TaskRepository;

import java.util.List;
import java.util.Map;

import static io.fluxion.remote.core.constants.BrokerRemoteConstant.API_JOB_WORKERS;

/**
 * @author Devil
 */
public class MapReduceJobTracker extends DistributedJobTracker {

    public MapReduceJobTracker(Job job, Executor executor, WorkerContext workerContext, TaskRepository taskRepository) {
        super(job, executor, workerContext, taskRepository);
    }

    @Override
    public void run() {
        MapReduceExecutor executor = (MapReduceExecutor) this.executor;
        List<Task> tasks = executor.sharding(JobContext.from(job));
        for (Task task : tasks) {
            task.setStatus(TaskStatus.CREATED);
            task.setRemoteNode(workerContext.node());
        }

        // 保存
        taskRepository.batchSave(tasks);
        taskCounter.getTotal().set(tasks.size());

        // 下发
        dispatch(tasks);
    }

    @Override
    public void success() {
        MapExecutor executor = (MapExecutor) this.executor;
        if (taskCounter.getFail().get() > 0) {
            job.fail("Task Execute Fail");
            report();
        } else {
            if (executor instanceof MapReduceExecutor) {
                MapReduceExecutor reduceExecutor = (MapReduceExecutor) this.executor;
                Map<String, String> allSubTaskResult = taskRepository.getAllSubTaskResult(job.getId());
                reduceExecutor.reduce(allSubTaskResult);
            }
            report();
        }
    }

    @Override
    public void fail() {
        job.fail("Task Execute Fail");
        report();
    }

    @Override
    protected Node findWorker(Task task) {
        JobWorkersRequest request = new JobWorkersRequest();
        request.setJobId(task.getJobId());
        request.setLoadBalanceSelect(true);
        request.setFilterResource(true);
        Response<JobWorkersResponse> response = workerContext.call(API_JOB_WORKERS, request);
        if (!response.success()) {
            return null;
        }
        List<NodeDTO> workers = response.getData().getWorkers();
        NodeDTO dto = workers.stream().findFirst().orElse(null);
        return WorkerClientConverter.toNode(dto);
    }

}
