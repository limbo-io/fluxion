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

import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.remote.core.api.Response;
import io.fluxion.remote.core.api.request.worker.TaskDispatchRequest;
import io.fluxion.remote.core.api.request.worker.TaskReportRequest;
import io.fluxion.remote.core.api.response.worker.TaskReportResponse;
import io.fluxion.remote.core.cluster.Node;
import io.fluxion.remote.core.constants.TaskStatus;
import io.fluxion.worker.core.WorkerContext;
import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.job.Job;
import io.fluxion.worker.core.remote.WorkerClientConverter;
import io.fluxion.worker.core.task.Task;
import io.fluxion.worker.core.task.repository.TaskRepository;
import org.apache.commons.lang3.BooleanUtils;

import java.time.LocalDateTime;
import java.util.List;

import static io.fluxion.remote.core.constants.WorkerRemoteConstant.API_TASK_DISPATCH;

/**
 * @author Devil
 */
public abstract class DistributedJobTracker extends JobTracker {

    protected final TaskRepository taskRepository;

    public DistributedJobTracker(Job job, Executor executor, WorkerContext workerContext, TaskRepository taskRepository) {
        super(job, executor, workerContext);
        this.taskRepository = taskRepository;
    }

    public abstract void success();

    public abstract void fail();

    protected abstract Node findWorker(Task task);

    /**
     * 下发task给其它worker
     */
    protected boolean dispatch(Task task) {
        Node worker = null;
        while (task.getDispatchFailTimes() < 3) {
            try {
                worker = findWorker(task);
                if (worker == null) {
                    break;
                }

                TaskDispatchRequest request = new TaskDispatchRequest();
                request.setJobId(task.getJobId());
                request.setTaskId(task.getId());
                request.setExecutorName(executor.name());
                request.setRemoteNode(WorkerClientConverter.toDTO(task.getRemoteNode()));
                Response<Boolean> response = workerContext.call(API_TASK_DISPATCH, worker.host(), worker.port(), request);
                if (response.success() && BooleanUtils.isTrue(response.getData())) {
                    boolean updated = taskRepository.dispatched(task.getJobId(), task.getId(), worker.address());
                    if (!updated) {
                        // 如果下发的worker先执行任务，会将状态改为running
                        log.warn("Task dispatch update fail: task:{}", JacksonUtils.toJSONString(task));
                    }
                    return true;
                } else {
                    task.dispatchFail();
                }
            } catch (Exception e) {
                log.error("Task dispatch failed: jobId:{} taskId:{} worker:{}",
                    task.getJobId(), task.getId(), worker == null ? null : worker.address(), e
                );
            }
        }
        task.setErrorMsg(String.format("task dispatch fail over limit last worker=%s", worker == null ? null : worker.address()));
        taskRepository.fail(task);
        return false;
    }

    protected void dispatch(List<Task> tasks) {
        for (Task task : tasks) {
            boolean dispatched = dispatch(task);
            if (!dispatched) {
                taskCounter.getFail().incrementAndGet();
            }
        }
    }

    public TaskReportResponse report(TaskReportRequest request) {
        Task task = taskRepository.getById(request.getJobId(), request.getTaskId());
        TaskReportResponse response = new TaskReportResponse();
        if (task == null) {
            response.setSuccess(false);
            return response;
        }
        response.setWorkerNode(WorkerClientConverter.toDTO(task.getWorkerNode()));
        response.setStatus(task.getStatus().value);
        if (!task.sameWorker(WorkerClientConverter.toNode(request.getWorkerNode()))) {
            response.setSuccess(false);
            return response;
        }
        Node worker = WorkerClientConverter.toNode(request.getWorkerNode());
        String workerAddress = worker == null ? null : worker.address();
        TaskStatus reqStatus = TaskStatus.parse(request.getStatus());
        LocalDateTime reportAt = request.getReportAt();
        boolean success = false;
        switch (reqStatus) {
            case DISPATCHED:
                if (TaskStatus.CREATED == task.getStatus()) { // dispatch 后更新晚于 report
                    success = taskRepository.dispatched(task.getJobId(), task.getId(), workerAddress);
                } else if (TaskStatus.DISPATCHED == task.getStatus()) { // task 还没开始执行时候
                    success = taskRepository.report(task.getJobId(), task.getId(), TaskStatus.DISPATCHED, workerAddress, reportAt);
                }
                break;
            case RUNNING:
                if (TaskStatus.DISPATCHED == task.getStatus()) {
                    success = taskRepository.start(task.getJobId(), task.getId(), workerAddress, reportAt);
                } else if (TaskStatus.RUNNING == task.getStatus()) {
                    success = taskRepository.report(task.getJobId(), task.getId(), TaskStatus.RUNNING, workerAddress, reportAt);
                }
                break;
            case SUCCEED:
                success = success(request, task);
                break;
            case FAILED:
                success = fail(request, task);
                break;
        }
        response.setSuccess(success);
        return response;
    }

    /**
     * 更新 running 状态的task为success
     */
    public boolean success(TaskReportRequest request, Task task) {
        if (TaskStatus.RUNNING != task.getStatus()) {
            return false;
        }
        task.setStatus(TaskStatus.SUCCEED);
        task.setLastReportAt(request.getReportAt());
        task.setResult(request.getResult());
        boolean updated = taskRepository.success(task);
        if (!updated) {
            return false;
        }
        taskCounter.getSuccess().incrementAndGet();
        if (taskCounter.getTotal().get() != (taskCounter.getFail().get() + taskCounter.getSuccess().get())) {
            return true; // 等所有完成
        }
        job.success("");
        success();
        return true;
    }

    /**
     * 更新 running 状态的task为fail
     */
    public boolean fail(TaskReportRequest request, Task task) {
        if (TaskStatus.RUNNING != task.getStatus()) {
            return false;
        }
        task.setStatus(TaskStatus.FAILED);
        task.setLastReportAt(request.getReportAt());
        task.setErrorMsg(request.getErrorMsg());
        task.setErrorStackTrace(request.getErrorStackTrace());
        boolean updated = taskRepository.fail(task);
        if (!updated) {
            return false;
        }
        taskCounter.getFail().incrementAndGet();
        if (taskCounter.getTotal().get() != (taskCounter.getFail().get() + taskCounter.getSuccess().get())) {
            return true; // 等所有完成
        }
        fail();
        return true;
    }

}
