/*
 * Copyright 2024-2030 fluxion-io Team (https://github.com/fluxion-io).
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

package io.fluxion.worker.core.tracker;

import io.fluxion.common.thread.NamedThreadFactory;
import io.fluxion.remote.core.constants.TaskConstant;
import io.fluxion.worker.core.context.ThreadLocalContext;
import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.rpc.BrokerRpc;
import io.fluxion.worker.core.rpc.TaskRpc;
import io.fluxion.worker.core.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Task 跟踪器 管理task执行
 *
 * @author Devil
 * @since 2021/7/24
 */
public class TrackerBak implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(TrackerBak.class);

    enum Status {
        WAITING, RUNNING, SUCCEED, FAILED, CANCELED
    }

    private final ScheduledExecutorService scheduledReportPool;

    /**
     * 任务执行器
     */
    public final Executor executor;

    /**
     * 从 Broker 接收到的任务
     */
    public final Task task;

    /**
     * Rpc
     */
    public final TaskRpc rpc;

    /**
     * 当前任务被调度的 Future
     */
    private Future<?> scheduleFuture;

    /**
     * 任务执行状态
     */
    private final AtomicReference<Status> status;

    private ScheduledFuture<?> taskReportScheduledFuture;

    public TrackerBak(TaskRpc rpc, Executor executor, Task task) {
        this.scheduledReportPool = Executors.newSingleThreadScheduledExecutor(NamedThreadFactory.newInstance("FluxionWorkerTaskReporter"));
        this.executor = executor;
        this.task = task;
        this.rpc = rpc;

        this.status = new AtomicReference<>(Status.WAITING);
    }


    /**
     * 执行任务
     */
    @Override
    public void run() {
        if (!this.status.compareAndSet(Status.WAITING, Status.RUNNING)) {
            log.warn("Task won't execute due to status: [{}]", this.status.get());
            return;
        }

        try {
            // 反馈执行中 -- 排除由于网络问题导致的失败可能性
            boolean success = reportTaskExecuting(task, 3);
            if (!success) {
                return; // 可能已经下发给其它节点
            }

            // 开启任务上报
            this.taskReportScheduledFuture = scheduledReportPool.scheduleAtFixedRate(new StatusReportRunnable(task), 1, TaskConstant.TASK_REPORT_SECONDS, TimeUnit.SECONDS);

            // 执行任务
            executor.run(task);

            // 执行成功
            this.status.set(Status.SUCCEED);
            this.rpc.feedbackTaskSucceed(task);
        } catch (Exception e) {

            // 执行异常
            log.error("Task execute error", e);
            this.status.set(Status.FAILED);

            this.rpc.feedbackTaskFailed(task, e);

        } finally {
            // 最终都要移除任务
            if (taskReportScheduledFuture != null) {
                taskReportScheduledFuture.cancel(true);
            }
        }
    }

    /**
     * 状态上报
     */
    private class StatusReportRunnable implements Runnable {

        private final Task task;

        public StatusReportRunnable(Task task) {
            this.task = task;
        }

        @Override
        public void run() {
            rpc.reportTask(task);
        }
    }


    /**
     * 取消当前任务上下文的执行
     *
     * @return 任务是否被成功取消。如果返回 false，可能是任务已经开始执行，或执行完成。
     */
    public boolean cancel() {
        if (this.scheduleFuture != null) {
            this.scheduleFuture.cancel(true);
        }

        return this.status.compareAndSet(Status.WAITING, Status.CANCELED);
    }

    private boolean reportTaskExecuting(Task task, int retryTimes) {
        if (retryTimes < 0) {
            return false;
        }
        try {
            return rpc.reportTaskExecuting(task);
        } catch (Exception e) {
            log.error("ReportTaskExecuting fail task={} times={}", task.taskId(), retryTimes, e);
            retryTimes--;
            return reportTaskExecuting(task, retryTimes);
        }
    }

    public void scheduleFuture(Future<?> scheduleFuture) {
        this.scheduleFuture = scheduleFuture;
    }

    public Task task() {
        return task;
    }

}
