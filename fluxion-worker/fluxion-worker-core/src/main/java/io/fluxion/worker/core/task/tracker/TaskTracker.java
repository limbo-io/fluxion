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
import io.fluxion.remote.core.api.request.TaskDispatchedRequest;
import io.fluxion.remote.core.api.request.TaskFailRequest;
import io.fluxion.remote.core.api.request.TaskReportRequest;
import io.fluxion.remote.core.api.request.TaskStartRequest;
import io.fluxion.remote.core.api.request.TaskSuccessRequest;
import io.fluxion.remote.core.constants.WorkerRemoteConstant;
import io.fluxion.worker.core.AbstractTracker;
import io.fluxion.worker.core.WorkerContext;
import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.remote.WorkerClientConverter;
import io.fluxion.worker.core.task.Task;
import org.apache.commons.lang3.BooleanUtils;

import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.fluxion.remote.core.constants.WorkerRemoteConstant.API_TASK_DISPATCHED;
import static io.fluxion.remote.core.constants.WorkerRemoteConstant.API_TASK_FAIL;
import static io.fluxion.remote.core.constants.WorkerRemoteConstant.API_TASK_REPORT;
import static io.fluxion.remote.core.constants.WorkerRemoteConstant.API_TASK_START;
import static io.fluxion.remote.core.constants.WorkerRemoteConstant.API_TASK_SUCCESS;

/**
 * Task执行管理和监控
 *
 * @author Devil
 */
public class TaskTracker extends AbstractTracker {

    private final Task task;

    private final WorkerContext workerContext;

    private final AtomicBoolean destroyed;

    private final Executor executor;

    private Future<?> processFuture;

    private Future<?> statusReportFuture;

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
            if (!reportDispatched()) {
                destroy();
                return true;
            }
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
        this.processFuture = workerContext.taskProcessExecutor().submit(() -> {
            try {
                // 反馈执行中 -- 排除由于网络问题导致的失败可能性
                boolean success = reportStart();
                if (!success) {
                    // 不成功，可能已经下发给其它节点
                    return;
                }
                executor.run(task);
                // 执行成功
                reportSuccess();
            } catch (Throwable throwable) {
                log.error("[TaskTracker] run error", throwable);
                reportFail(throwable);
            } finally {
                destroy();
            }
        });
        // 提交状态监控
        this.statusReportFuture = workerContext.taskStatusReportExecutor().scheduleAtFixedRate(() -> {
            TaskReportRequest request = new TaskReportRequest();
            request.setTaskId(task.getId());
            request.setJobId(task.getJobId());
            request.setReportAt(TimeUtils.currentLocalDateTime());
            request.setWorkerNode(WorkerClientConverter.toDTO(workerContext.node()));
            NodeDTO remote = WorkerClientConverter.toDTO(task.getRemoteNode());
            workerContext.call(API_TASK_REPORT, remote.getHost(), remote.getPort(), request);
        }, 1, WorkerRemoteConstant.TASK_REPORT_SECONDS, TimeUnit.SECONDS);
    }

    private boolean reportDispatched() {
        try {
            TaskDispatchedRequest request = new TaskDispatchedRequest();
            request.setTaskId(task.getId());
            request.setJobId(task.getJobId());
            request.setWorkerNode(WorkerClientConverter.toDTO(workerContext.node()));
            NodeDTO remote = WorkerClientConverter.toDTO(task.getRemoteNode());
            Response<Boolean> response = workerContext.call(API_TASK_DISPATCHED, remote.getHost(), remote.getPort(), request);
            return response.success() && BooleanUtils.isTrue(response.getData());
        } catch (Exception e) {
            log.error("reportDispatched fail taskId={}", task.getId(), e);
            return false;
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
        workerContext.deleteTask(task.getId());
        log.info("TaskTracker task: {} destroyed success", JacksonUtils.toJSONString(task));
    }

    private boolean reportStart() {
        try {
            TaskStartRequest request = new TaskStartRequest();
            request.setTaskId(task.getId());
            request.setJobId(task.getJobId());
            request.setWorkerNode(WorkerClientConverter.toDTO(workerContext.node()));
            request.setReportAt(TimeUtils.currentLocalDateTime());
            NodeDTO remote = WorkerClientConverter.toDTO(task.getRemoteNode());
            Response<Boolean> response = workerContext.call(API_TASK_START, remote.getHost(), remote.getPort(), request);
            return response.success() && BooleanUtils.isTrue(response.getData());
        } catch (Exception e) {
            log.error("reportStart fail taskId={}", task.getId(), e);
            return false;
        }
    }

    private void reportSuccess() {
        try {
            TaskSuccessRequest request = new TaskSuccessRequest();
            request.setTaskId(task.getId());
            request.setJobId(task.getJobId());
            request.setReportAt(TimeUtils.currentLocalDateTime());
            request.setWorkerNode(WorkerClientConverter.toDTO(workerContext.node()));
            NodeDTO remote = WorkerClientConverter.toDTO(task.getRemoteNode());
            workerContext.call(API_TASK_SUCCESS, remote.getHost(), remote.getPort(), request); // todo @d later 如果上报失败需要记录，定时重试
        } catch (Exception e) {
            log.error("reportSuccess fail taskId={}", task.getId(), e);
            // todo @d later 如果上报失败需要记录，定时重试
        }
    }

    private void reportFail(Throwable throwable) {
        try {
            TaskFailRequest request = new TaskFailRequest();
            request.setTaskId(task.getId());
            request.setJobId(task.getJobId());
            request.setReportAt(TimeUtils.currentLocalDateTime());
            request.setWorkerNode(WorkerClientConverter.toDTO(workerContext.node()));
            request.setErrorMsg(throwable.getMessage());
            NodeDTO remote = WorkerClientConverter.toDTO(task.getRemoteNode());
            workerContext.call(API_TASK_FAIL, remote.getHost(), remote.getPort(), request); // todo @d later 如果上报失败需要记录，定时重试
        } catch (Exception e) {
            log.error("reportFail fail taskId={}", task.getId(), e);
            // todo @d later 如果上报失败需要记录，定时重试
        }
    }

    public Task task() {
        return task;
    }

}
