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
import io.fluxion.remote.core.api.request.TaskDispatchRequest;
import io.fluxion.worker.core.WorkerContext;
import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.job.Job;
import io.fluxion.worker.core.task.Task;
import io.fluxion.worker.core.task.repository.TaskRepository;
import org.apache.commons.lang3.BooleanUtils;

import static io.fluxion.remote.core.constants.WorkerRemoteConstant.API_TASK_DISPATCH;

/**
 * @author Devil
 */
public abstract class DistributedJobTracker extends JobTracker {

    public DistributedJobTracker(Job job, Executor executor, WorkerContext workerContext) {
        super(job, executor, workerContext);
    }

    public abstract void success(Task task);

    public abstract void fail(Task task);

    protected abstract NodeDTO findWorker(Task task);

    protected boolean dispatch(Task task) {
        TaskRepository taskRepository = workerContext.taskRepository();
        while (task.getDispatchFailTimes() < 3) {
            try {
                NodeDTO worker = findWorker(task);
                task.setWorkerAddress(worker.address());
                TaskDispatchRequest request = new TaskDispatchRequest();
                request.setJobId(task.getJobId());
                request.setTaskId(task.getId());
                request.setExecutorName(executor.name());
                request.setRemoteAddress(task.getRemoteAddress());
                Response<Boolean> response = workerContext.call(API_TASK_DISPATCH, worker.getHost(), worker.getPort(), request);
                if (response.success() && BooleanUtils.isTrue(response.getData())) {
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

}
