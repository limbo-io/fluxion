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
import io.fluxion.remote.core.constants.TaskStatus;
import io.fluxion.worker.core.WorkerContext;
import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.executor.MapExecutor;
import io.fluxion.worker.core.executor.MapReduceExecutor;
import io.fluxion.worker.core.job.Job;
import io.fluxion.worker.core.task.Task;
import io.fluxion.worker.core.task.repository.TaskRepository;

import java.util.List;
import java.util.Map;

import static io.fluxion.remote.core.constants.BrokerRemoteConstant.API_JOB_WORKERS;
import static io.fluxion.remote.core.constants.WorkerRemoteConstant.API_TASK_DISPATCH;

/**
 * @author Devil
 */
public class MapReduceJobTracker extends DistributedJobTracker {

    public MapReduceJobTracker(Job job, Executor executor, WorkerContext workerContext) {
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

            MapReduceExecutor executor = (MapReduceExecutor) this.executor;
            List<Task> tasks = executor.sharding(job);
            for (Task task : tasks) {
                task.setStatus(TaskStatus.CREATED);
                task.setRemoteAddress(workerContext.address());
            }

            // 保存
            TaskRepository taskRepository = workerContext.taskRepository();
            taskRepository.batchSave(tasks);
            taskCounter.getTotal().set(tasks.size());

            // 下发
            boolean dispatched = false;
            for (Task task : tasks) {
                dispatched = dispatch(task);
                if (dispatched) {
                    taskRepository.dispatched(task);
                } else {
                    task.setErrorMsg(String.format("task dispatch fail over limit last worker=%s", task.getWorkerAddress()));
                    taskRepository.fail(task);
                    break;
                }
            }
            if (!dispatched) {
                reportFail("dispatch fail", taskMonitor());
            }
        } catch (Throwable throwable) {
            log.error("[{}] run error", getClass().getSimpleName(), throwable);
            reportFail(throwable.getMessage(), taskMonitor());
            destroy();
        }
    }

    @Override
    public void success(Task task) {
        MapExecutor executor = (MapExecutor) this.executor;
        taskCounter.getSuccess().incrementAndGet();
        if (taskCounter.getTotal().get() != (taskCounter.getFail().get() + taskCounter.getSuccess().get())) {
            return;
        }
        if (taskCounter.getFail().get() > 0) {
            reportFail("", taskMonitor());
        } else {
            if (executor instanceof MapReduceExecutor) {
                MapReduceExecutor reduceExecutor = (MapReduceExecutor) this.executor;
                Map<String, String> allSubTaskResult = workerContext.taskRepository().getAllSubTaskResult(task.getJobId());
                reduceExecutor.reduce(allSubTaskResult);
            }
            reportSuccess(taskMonitor());
        }
        destroy();
    }

    @Override
    public void fail(Task task) {
        taskCounter.getSuccess().incrementAndGet();
        if (taskCounter.getTotal().get() != (taskCounter.getFail().get() + taskCounter.getSuccess().get())) {
            return;
        }
        reportFail("", taskMonitor());
        destroy();
    }

    private boolean dispatch(Task task) {
        TaskRepository taskRepository = workerContext.taskRepository();
        while (task.getDispatchFailTimes() < 3) {
            try {
                NodeDTO worker = findWorker();
                task.setWorkerAddress(worker.getHost() + ":" + worker.getPort());
                TaskDispatchRequest request = new TaskDispatchRequest();
                request.setJobId(task.getJobId());
                request.setTaskId(task.getId());
                request.setExecutorName(executor.name());
                request.setRemoteAddress(task.getRemoteAddress());
                boolean dispatched = workerContext.call(API_TASK_DISPATCH, worker.getHost(), worker.getPort(), request);
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
        JobWorkersRequest request = new JobWorkersRequest();
        request.setJobId(job.getId());
        request.setLoadBalanceSelect(true);
        request.setFilterResource(true);
        List<NodeDTO> workers = workerContext.call(API_JOB_WORKERS, request).getWorkers();
        return workers.stream().findFirst().orElse(null);
    }
}
