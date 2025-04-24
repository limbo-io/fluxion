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
import io.fluxion.remote.core.api.response.worker.TaskReportResponse;
import io.fluxion.remote.core.cluster.Node;
import io.fluxion.remote.core.constants.TaskStatus;
import io.fluxion.remote.core.constants.WorkerRemoteConstant;
import io.fluxion.worker.core.AbstractTracker;
import io.fluxion.worker.core.WorkerContext;
import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.remote.WorkerClientConverter;
import io.fluxion.worker.core.task.Task;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.fluxion.remote.core.constants.WorkerRemoteConstant.API_TASK_REPORT;

/**
 * Task执行管理和监控
 *
 * @author Devil
 */
public class TaskTracker extends AbstractTracker {
    /**
     * 上报失败最大次数
     */
    private static final int MAX_REPORT_FAILED_TIMES = 5;

    private final Task task;

    private final WorkerContext workerContext;

    private final AtomicBoolean destroyed;

    private final Executor executor;

    private Future<?> processFuture;

    private Future<?> statusReportFuture;
    /**
     * 上报失败统计
     */
    private int reportFailedCount = 0;

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
        task.setStatus(TaskStatus.DISPATCHED);
        // 提交执行 正常来说保存成功这里不会被拒绝
        this.processFuture = workerContext.processExecutor().submit(() -> {
            try {
                // 反馈执行中 -- 排除由于网络问题导致的失败可能性
                task.setStatus(TaskStatus.RUNNING);
                if (!report().isSuccess()) {
                    // 不成功，可能已经下发给其它节点
                    return;
                }
                executor.run(task);
                // 执行成功
                task.setStatus(TaskStatus.SUCCEED);
                report();
            } catch (Throwable throwable) {
                log.error("[TaskTracker] run error", throwable);
                task.setStatus(TaskStatus.FAILED);
                task.setErrorMsg(throwable.getMessage());
                task.setErrorStackTrace(ExceptionUtils.getStackTrace(throwable));
                report();
            }
        });
        // 提交状态监控
        this.statusReportFuture = workerContext.statusReportExecutor().scheduleAtFixedRate(
            this::report, 1, WorkerRemoteConstant.TASK_REPORT_SECONDS, TimeUnit.SECONDS
        );
    }

    private TaskReportResponse report() {
        task.setLastReportAt(TimeUtils.currentLocalDateTime());
        TaskReportRequest request = new TaskReportRequest();
        TaskReportResponse result;
        try {
            request.setTaskId(task.getId());
            request.setJobId(task.getJobId());
            request.setReportAt(task.getLastReportAt());
            request.setStatus(task.getStatus().value);
            request.setWorkerNode(WorkerClientConverter.toDTO(workerContext.node()));

            request.setResult(task.getResult());

            request.setErrorMsg(task.getErrorMsg());
            request.setErrorStackTrace(task.getErrorStackTrace());

            NodeDTO remote = WorkerClientConverter.toDTO(task.getRemoteNode());
            Response<TaskReportResponse> response = workerContext.call(API_TASK_REPORT, remote.getHost(), remote.getPort(), request);
            if (!response.success() || response.getData() == null) {
                result = new TaskReportResponse();
            } else {
                result = response.getData();
            }
        } catch (Exception e) {
            log.error("[TaskReport] fail request={}", JacksonUtils.toJSONString(request), e);
            result = new TaskReportResponse();
        }
        Node currentWorker = WorkerClientConverter.toNode(result.getWorkerNode());
        if (!result.isSuccess() && !task.sameWorker(currentWorker)) {
            log.warn("[TaskReport] task worker change jobId:{} taskId:{} currentWorker:{}",
                task.getJobId(), task.getId(), currentWorker == null ? null : currentWorker.address()
            );
            destroy();
            return result;
        }
        if (task.getStatus().isFinished()) {
            if (result.isSuccess()) {
                destroy();
            } else {
                reportFailedCount++;
                if (reportFailedCount > MAX_REPORT_FAILED_TIMES) {
                    log.warn("[TaskReport] fail more than {} times jobId:{} taskId:{}", MAX_REPORT_FAILED_TIMES, task.getJobId(), task.getId());
                    destroy();
                }
            }
        }
        return result;
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

    public Task task() {
        return task;
    }

}
