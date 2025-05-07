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

package io.fluxion.worker.core.task.tracker;

import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.remote.core.api.Response;
import io.fluxion.remote.core.api.dto.NodeDTO;
import io.fluxion.remote.core.api.request.worker.TaskReportRequest;
import io.fluxion.remote.core.api.request.worker.TaskStateTransitionRequest;
import io.fluxion.remote.core.api.response.worker.TaskStateTransitionResponse;
import io.fluxion.remote.core.constants.TaskStateEvent;
import io.fluxion.remote.core.constants.TaskStatus;
import io.fluxion.remote.core.constants.WorkerRemoteConstant;
import io.fluxion.worker.core.AbstractTracker;
import io.fluxion.worker.core.WorkerContext;
import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.remote.WorkerClientConverter;
import io.fluxion.worker.core.task.Task;
import io.fluxion.worker.core.task.TaskContext;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.fluxion.remote.core.constants.WorkerRemoteConstant.API_TASK_REPORT;
import static io.fluxion.remote.core.constants.WorkerRemoteConstant.API_TASK_STATE_TRANSITION;

/**
 * Task执行管理和监控
 *
 * @author Devil
 */
public class TaskTracker extends AbstractTracker {
    /**
     * 上报失败最大次数
     */
    private static final int MAX_FINISH_FAILED_TIMES = 5;

    private final Task task;

    private final WorkerContext workerContext;

    private final AtomicBoolean destroyed;

    private final Executor executor;

    private Future<?> processFuture;

    private Future<?> statusReportFuture;

    protected Future<?> finishReportFuture;
    /**
     * 上报失败统计
     */
    private int finishFailedCount = 0;

    public TaskTracker(Task task, Executor executor, WorkerContext workerContext) {
        this.task = task;
        this.executor = executor;
        this.workerContext = workerContext;
        this.destroyed = new AtomicBoolean(false);
    }

    public boolean start() {
        if (!workerContext.status().isRunning()) {
            log.info("Worker is not running: {}", workerContext.status());
            return false;
        }
        if (!workerContext.saveTask(this)) {
            log.info("Receive task [{}], but already in repository", task.getId());
            return true;
        }

        try {
            run();
            return true;
        } catch (RejectedExecutionException e) {
            log.error("Schedule Task in worker failed, maybe work thread exhausted task:{}", JacksonUtils.toJSONString(task), e);
            destroy();
            return false;
        } catch (Exception e) {
            log.error("Schedule Task in worker failed, task:{}", JacksonUtils.toJSONString(task), e);
            destroy();
            return false;
        }
    }

    private void run() {
        // 提交执行 正常来说保存成功这里不会被拒绝
        this.processFuture = workerContext.processExecutor().submit(() -> {
            try {
                // 反馈执行中 -- 排除由于网络问题导致的失败可能性
                taskStart();
                if (destroyed.get()) {
                    return;
                }
                executor.run(new TaskContext(task.getId(), task.getJobId()));
                // 执行成功
                taskSuccess("");
            } catch (Throwable throwable) {
                log.error("[TaskTracker] run error", throwable);
                taskFail(throwable.getMessage(), ExceptionUtils.getStackTrace(throwable));
            }
        });
        // 提交状态监控
        this.statusReportFuture = workerContext.statusReportExecutor().scheduleAtFixedRate(
            this::report, 1, WorkerRemoteConstant.TASK_REPORT_SECONDS, TimeUnit.SECONDS
        );
    }

    private void statTransition(TaskStateEvent event) {
        task.setLastReportAt(TimeUtils.currentLocalDateTime());
        TaskStateTransitionRequest request = new TaskStateTransitionRequest();
        try {
            request.setTaskId(task.getId());
            request.setJobId(task.getJobId());
            request.setReportAt(task.getLastReportAt());
            request.setEvent(event.value);
            request.setWorkerNode(WorkerClientConverter.toDTO(workerContext.node()));

            request.setResult(task.getResult());

            request.setErrorMsg(task.getErrorMsg());
            request.setErrorStackTrace(task.getErrorStackTrace());

            NodeDTO remote = WorkerClientConverter.toDTO(task.getRemoteNode());
            Response<TaskStateTransitionResponse> response = workerContext.call(API_TASK_STATE_TRANSITION, remote.getHost(), remote.getPort(), request);
            if (response.success() && response.getData() != null && response.getData().isSuccess()) {
                return;
            }
            if (!task.getStatus().isFinished()) {
                destroy();
                return;
            }
            finishFailedCount++;
            if (finishFailedCount > MAX_FINISH_FAILED_TIMES) {
                log.warn("[TaskFinish] fail more than {} times jobId:{} taskId:{} event:{}", MAX_FINISH_FAILED_TIMES, task.getJobId(), task.getId(), event);
                destroy();
                return;
            }
            // 启动定时尝试
            startFinishSchedule(event);
        } catch (Exception e) {
            log.error("[TaskStatTransition] fail request={}", JacksonUtils.toJSONString(request), e);
            // 启动定时尝试
            startFinishSchedule(event);
        }
    }

    /**
     * 启动定时尝试
     * @param event 状态事件
     */
    private void startFinishSchedule(TaskStateEvent event) {
        this.finishReportFuture = workerContext.statusReportExecutor().scheduleAtFixedRate(
            () -> statTransition(event), 1, 2, TimeUnit.MINUTES
        );
    }

    private void report() {
        task.setLastReportAt(TimeUtils.currentLocalDateTime());
        TaskReportRequest request = new TaskReportRequest();
        try {
            request.setTaskId(task.getId());
            request.setJobId(task.getJobId());
            request.setReportAt(task.getLastReportAt());
            request.setStatus(task.getStatus().value);
            request.setWorkerNode(WorkerClientConverter.toDTO(workerContext.node()));

            NodeDTO remote = WorkerClientConverter.toDTO(task.getRemoteNode());
            workerContext.call(API_TASK_REPORT, remote.getHost(), remote.getPort(), request);
        } catch (Exception e) {
            log.error("[TaskReport] fail request={}", JacksonUtils.toJSONString(request), e);
        }
    }

    public void destroy() {
        if (!destroyed.compareAndSet(false, true)) {
            log.info("TaskTracker taskId: {} has been destroyed", task.getId());
            return;
        }
        if (statusReportFuture != null) {
            statusReportFuture.cancel(true);
        }
        if (processFuture != null) {
            processFuture.cancel(true);
        }
        if (finishReportFuture != null) {
            finishReportFuture.cancel(true);
        }
        workerContext.deleteTask(task.getId());
        log.info("TaskTracker task: {} destroyed success", JacksonUtils.toJSONString(task));
    }

    public Task task() {
        return task;
    }

    protected void taskStart() {
        task.setStatus(TaskStatus.RUNNING);
        statTransition(TaskStateEvent.START);
    }

    protected void taskSuccess(String result) {
        task.setStatus(TaskStatus.SUCCEED);
        task.setResult(result);
        statTransition(TaskStateEvent.RUN_SUCCESS);
    }

    protected void taskFail(String errorMsg, String errorStackTrace) {
        task.setStatus(TaskStatus.FAILED);
        task.setErrorMsg(errorMsg);
        task.setErrorStackTrace(errorStackTrace);
        statTransition(TaskStateEvent.RUN_FAIL);
    }

}
