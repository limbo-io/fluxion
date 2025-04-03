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

import io.fluxion.remote.core.api.dto.NodeDTO;
import io.fluxion.remote.core.api.request.JobWorkersRequest;
import io.fluxion.remote.core.api.request.TaskDispatchRequest;
import io.fluxion.worker.core.WorkerContext;
import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.executor.MapReduceExecutor;
import io.fluxion.worker.core.job.Job;
import io.fluxion.worker.core.task.Task;
import io.fluxion.worker.core.task.repository.TaskRepository;

import java.util.List;

import static io.fluxion.remote.core.constants.BrokerRemoteConstant.API_JOB_WORKERS;
import static io.fluxion.remote.core.constants.WorkerRemoteConstant.API_TASK_DISPATCH;

/**
 * @author Devil
 */
public class MapReduceJobTracker extends JobTracker {

    private final MapReduceExecutor executor;

    public MapReduceJobTracker(Job job, Executor executor, WorkerContext workerContext) {
        super(job, workerContext);
        this.executor = (MapReduceExecutor) executor;
    }

    @Override
    public void run() {
        List<Task> tasks = executor.sharding(job);

        // 保存
        TaskRepository taskRepository = workerContext.taskRepository();
        taskRepository.batchSave(tasks);

        // 下发
        boolean dispatched = false;
        for (Task task : tasks) {
            dispatched = dispatch(task);
            if (dispatched) {
                taskRepository.dispatched(task.getJobId(), task.getId(), task.getWorkerAddress());
            } else {
                task.setErrorMsg(String.format("task dispatch fail over limit last worker=%s", task.getWorkerAddress()));
                taskRepository.fail(task);
                break;
            }
        }
        if (!dispatched) {
            reportFail("dispatch fail");
        }
    }

    private boolean dispatch(Task task) {
        TaskRepository taskRepository = workerContext.taskRepository();
        while (task.getDispatchFailTimes() < 3) {
            try {
                NodeDTO worker = findWorker();
                task.setWorkerAddress(worker.address());

                TaskDispatchRequest request = new TaskDispatchRequest();
                request.setJobId(task.getJobId());
                request.setTaskId(task.getId());
                request.setExecutorName(executor.name());
                request.setRemoteAddress(task.getRemoteAddress());
                boolean dispatched = workerContext.call(API_TASK_DISPATCH, request);
                if (dispatched) {
                    return true;
                } else {
                    taskRepository.dispatchFail(task.getJobId(), task.getId());
                }
            } catch (Exception e) {
                log.error("Task dispatch failed: jobId={} taskId={} worker={}", task.getJobId(), task.getId(), task.getWorkerAddress(), e);
            }
        }
        return false;
    }

    private NodeDTO findWorker() {
        JobWorkersRequest request = new JobWorkersRequest(job.getId(), true, true);
        List<NodeDTO> workers = workerContext.call(API_JOB_WORKERS, request).getWorkers();
        return workers.stream().findFirst().orElse(null);
    }
}
