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
import io.fluxion.remote.core.api.request.JobWorkersRequest;
import io.fluxion.remote.core.api.response.JobWorkersResponse;
import io.fluxion.remote.core.constants.TaskStatus;
import io.fluxion.worker.core.WorkerContext;
import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.job.Job;
import io.fluxion.worker.core.task.Task;
import io.fluxion.worker.core.task.repository.TaskRepository;

import java.util.ArrayList;
import java.util.List;

import static io.fluxion.remote.core.constants.BrokerRemoteConstant.API_JOB_WORKERS;

/**
 * @author Devil
 */
public class BroadcastJobTracker extends DistributedJobTracker {

    public BroadcastJobTracker(Job job, Executor executor, WorkerContext workerContext) {
        super(job, executor, workerContext);
    }

    @Override
    public void run() {
        try {
            // 反馈执行中 -- 排除由于网络问题导致的失败可能性
            boolean success = reportStart();
            if (!success) {
                // 不成功，可能已经下发给其它节点
                return;
            }

            // 获取所有节点，创建task
            JobWorkersRequest request = new JobWorkersRequest();
            request.setJobId(job.getId());
            Response<JobWorkersResponse> workerResponse = workerContext.call(API_JOB_WORKERS, request);
            if (!workerResponse.success()) {
                reportFail("Get Workers Fail");
                destroy();
                return;
            }

            List<NodeDTO> workers = workerResponse.getData().getWorkers();

            List<Task> tasks = new ArrayList<>();
            for (int i = 0; i < workers.size(); i++) {
                NodeDTO worker = workers.get(i);
                Task task = new Task("SUB_" + i, job.getId());
                task.setStatus(TaskStatus.CREATED);
                task.setRemoteAddress(workerContext.address());
                task.setWorkerAddress(worker.address());
                tasks.add(task);
            }

            // 保存
            TaskRepository taskRepository = workerContext.taskRepository();
            taskRepository.batchSave(tasks);
            taskCounter.getTotal().set(tasks.size());

            // 下发
            for (Task task : tasks) {
                boolean dispatched = dispatch(task);
                if (dispatched) {
                    taskRepository.dispatched(task);
                } else {
                    task.setErrorMsg(String.format("task dispatch fail over limit last worker=%s", task.getWorkerAddress()));
                    taskRepository.fail(task);
                }
            }
            // todo ! 如果全部下发失败了，直接失败
        } catch (Throwable throwable) {
            log.error("[{}] run error", getClass().getSimpleName(), throwable);
            reportFail(throwable.getMessage()); // todo ! 这里没有统计失败个数
            destroy();
        }
    }

    @Override
    public void success(Task task) {
        taskCounter.getSuccess().incrementAndGet();
        if (taskCounter.getTotal().get() != (taskCounter.getFail().get() + taskCounter.getSuccess().get())) {
            return;
        }
        reportSuccess();
        destroy();
    }

    @Override
    public void fail(Task task) {
        taskCounter.getFail().incrementAndGet();
        if (taskCounter.getTotal().get() != (taskCounter.getFail().get() + taskCounter.getSuccess().get())) {
            return;
        }
        if (taskCounter.getSuccess().get() > 0) {
            reportSuccess();
        } else {
            reportFail("");
        }
        destroy();
    }

    @Override
    protected NodeDTO findWorker(Task task) {
        return null; // todo
    }

}
