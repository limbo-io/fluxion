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

import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.remote.core.api.Response;
import io.fluxion.remote.core.api.dto.JobMonitorDTO;
import io.fluxion.remote.core.api.request.worker.TaskDispatchRequest;
import io.fluxion.remote.core.api.request.worker.TaskReportRequest;
import io.fluxion.remote.core.api.request.worker.TaskStateTransitionRequest;
import io.fluxion.remote.core.api.response.worker.TaskReportResponse;
import io.fluxion.remote.core.api.response.worker.TaskStateTransitionResponse;
import io.fluxion.remote.core.cluster.Node;
import io.fluxion.remote.core.constants.TaskStateEvent;
import io.fluxion.remote.core.constants.TaskStatus;
import io.fluxion.worker.core.WorkerContext;
import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.job.Job;
import io.fluxion.worker.core.remote.WorkerClientConverter;
import io.fluxion.worker.core.task.Task;
import io.fluxion.worker.core.task.repository.TaskRepository;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static io.fluxion.remote.core.constants.WorkerRemoteConstant.API_TASK_DISPATCH;

/**
 * @author Devil
 */
public abstract class DistributedJobTracker extends JobTracker {

    private Future<?> taskCheckFuture;

    protected final TaskCounter taskCounter;

    private final TaskRepository taskRepository;

    public DistributedJobTracker(Job job, Executor executor, WorkerContext workerContext, TaskRepository taskRepository) {
        super(job, executor, workerContext);
        this.taskCounter = new TaskCounter();
        this.taskRepository = taskRepository;
    }

    public abstract void success();

    public abstract void fail();

    protected abstract Node findWorker(Task task);

    @Override
    protected void postProcessOnStart() {
        this.taskCheckFuture = null; // todo
    }

    @Override
    protected void postProcessOnDestroy() {
        if (taskCheckFuture != null) {
            taskCheckFuture.cancel(true);
        }
    }

    /**
     * 下发task给其它worker
     */
    public void dispatch(Task task) {
        Node worker = null;
        while (task.getDispatchFailTimes() < 3) {
            try {
                worker = findWorker(task);
                if (worker == null) {
                    break;
                }

                TaskDispatchRequest request = new TaskDispatchRequest();
                request.setJobId(task.getJobId());
                request.setTaskId(task.getTaskId());
                request.setExecutorName(executor.name());
                request.setRemoteNode(WorkerClientConverter.toDTO(task.getRemoteNode()));
                Response<Boolean> response = workerContext.call(API_TASK_DISPATCH, worker.host(), worker.port(), request);
                if (response.success() && BooleanUtils.isTrue(response.getData())) {
                    return;
                } else {
                    task.dispatchFail();
                }
            } catch (Exception e) {
                log.error("Task dispatch failed: jobId:{} taskId:{} worker:{}",
                    task.getJobId(), task.getTaskId(), worker == null ? null : worker.address(), e
                );
            }
        }
        taskFail(
            task.getTaskId(),
            TimeUtils.currentLocalDateTime(),
            String.format("task dispatch fail over limit last worker=%s", worker == null ? null : worker.address()),
            null
        );
    }

    public void dispatch(List<Task> tasks) {
        for (Task task : tasks) {
            dispatch(task);
        }
    }

    public TaskStateTransitionResponse handleTaskStateTransitionRequest(TaskStateTransitionRequest request) {
        Task task = taskRepository.getById(request.getJobId(), request.getTaskId());
        TaskStateTransitionResponse response = new TaskStateTransitionResponse();
        if (task == null) {
            response.setSuccess(false);
            return response;
        }
        Node worker = WorkerClientConverter.toNode(request.getWorkerNode());
        String workerAddress = worker == null ? null : worker.address();
        LocalDateTime reportAt = request.getReportAt();
        boolean success = false;
        switch (TaskStateEvent.parse(request.getEvent())) {
            case START:
                success = taskRepository.start(task.getJobId(), task.getTaskId(), workerAddress, reportAt);
                break;
            case RUN_SUCCESS:
                if (task.sameWorker(WorkerClientConverter.toNode(request.getWorkerNode()))) {
                    success = handleSuccessRequest(request, task);
                }
                break;
            case RUN_FAIL:
                if (task.sameWorker(WorkerClientConverter.toNode(request.getWorkerNode()))) {
                    success = handleFailRequest(request, task);
                }
                break;
        }
        response.setSuccess(success);
        return response;
    }

    public TaskReportResponse handleTaskReport(TaskReportRequest request) {
        Node worker = WorkerClientConverter.toNode(request.getWorkerNode());
        String workerAddress = worker == null ? null : worker.address();
        LocalDateTime reportAt = request.getReportAt();

        TaskReportResponse response = new TaskReportResponse();
        boolean success = taskRepository.report(request.getJobId(), request.getTaskId(), TaskStatus.parse(request.getStatus()), workerAddress, reportAt);
        response.setSuccess(success);
        return response;
    }

    /**
     * 更新 running 状态的task为success
     */
    public boolean handleSuccessRequest(TaskStateTransitionRequest request, Task task) {
        if (TaskStatus.RUNNING != task.getStatus()) {
            return false;
        }
        taskSuccess(task.getTaskId(), request.getReportAt(), request.getResult());
        success();
        return true;
    }

    /**
     * 更新 running 状态的task为fail
     */
    public boolean handleFailRequest(TaskStateTransitionRequest request, Task task) {
        if (TaskStatus.RUNNING != task.getStatus()) {
            return false;
        }
        taskFail(task.getTaskId(), request.getReportAt(), request.getErrorMsg(), request.getErrorStackTrace());
        fail();
        return true;
    }

    @Override
    protected JobMonitorDTO jobMonitor() {
        JobMonitorDTO dto = new JobMonitorDTO();
        dto.setTotalTaskNum(taskCounter.total());
        dto.setSuccessTaskNum(taskCounter.success());
        dto.setFailTaskNum(taskCounter.fail());
        return dto;
    }

    protected void createTasks(List<Task> tasks) {
        if (CollectionUtils.isEmpty(tasks)) {
            return;
        }
        taskRepository.batchSave(tasks);
        taskCounter.total.set(tasks.size());
    }

    private void taskSuccess(String taskId, LocalDateTime endAt, String result) {
        Task task = taskRepository.getById(job.getId(), taskId);
        task.setStatus(TaskStatus.SUCCEED);
        task.setLastReportAt(endAt);
        task.setEndAt(endAt);
        task.setResult(result);
        taskRepository.success(task);
        taskCounter.success.incrementAndGet();
    }

    private void taskFail(String taskId, LocalDateTime endAt, String errorMsg, String errorStack) {
        Task task = taskRepository.getById(job.getId(), taskId);
        task.setStatus(TaskStatus.FAILED);
        task.setLastReportAt(endAt);
        task.setEndAt(endAt);
        task.setErrorMsg(errorMsg);
        task.setErrorStackTrace(errorStack);
        taskRepository.fail(task);
        taskCounter.fail.incrementAndGet();
    }

    protected Map<String, String> getAllSubTaskResult(String jobId) {
        return taskRepository.getAllSubTaskResult(jobId);
    }

    protected static class TaskCounter {

        AtomicInteger total = new AtomicInteger(0);

        AtomicInteger success = new AtomicInteger(0);

        AtomicInteger fail = new AtomicInteger(0);

        public boolean isFinished() {
            return total.get() == success.get() + fail.get();
        }

        public int total() {
            return total.get();
        }

        public int success() {
            return success.get();
        }

        public int fail() {
            return fail.get();
        }
    }

}
