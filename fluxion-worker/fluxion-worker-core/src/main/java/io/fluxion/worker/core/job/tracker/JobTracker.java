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
import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.remote.core.api.Response;
import io.fluxion.remote.core.api.dto.JobMonitorDTO;
import io.fluxion.remote.core.api.request.broker.JobReportRequest;
import io.fluxion.remote.core.api.request.broker.JobStateTransitionRequest;
import io.fluxion.remote.core.api.response.broker.JobStateTransitionResponse;
import io.fluxion.remote.core.constants.BrokerRemoteConstant;
import io.fluxion.remote.core.constants.JobStateEvent;
import io.fluxion.remote.core.constants.JobStatus;
import io.fluxion.worker.core.AbstractTracker;
import io.fluxion.worker.core.WorkerContext;
import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.job.Job;
import io.fluxion.worker.core.remote.WorkerClientConverter;
import io.fluxion.worker.core.task.TaskContext;

import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.fluxion.remote.core.constants.BrokerRemoteConstant.API_JOB_REPORT;
import static io.fluxion.remote.core.constants.BrokerRemoteConstant.API_JOB_STATE_TRANSITION;

/**
 * Task执行管理和监控
 *
 * @author Devil
 */
public abstract class JobTracker extends AbstractTracker {
    /**
     * 上报失败最大次数
     */
    private static final int MAX_FINISH_FAILED_TIMES = 5;

    /**
     * 从 Broker 接收到的任务
     */
    protected final Job job;

    protected final Executor executor;

    protected final WorkerContext workerContext;

    private Future<?> processFuture;

    private Future<?> statusReportFuture;

    private final AtomicBoolean destroyed;

    protected Future<?> finishReportFuture;

    /**
     * 上报失败统计
     */
    private int finishFailedCount = 0;

    public JobTracker(Job job, Executor executor, WorkerContext workerContext) {
        this.job = job;
        this.executor = executor;
        this.workerContext = workerContext;
        this.destroyed = new AtomicBoolean(false);
    }

    @Override
    public boolean start() {
        try {
            // 提交执行 正常来说保存成功这里不会被拒绝
            this.processFuture = workerContext.processExecutor().submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        // 反馈执行中 -- 排除由于网络问题导致的失败可能性
                        jobStart();
                        if (destroyed.get()) {
                            return;
                        }

                        // 执行
                        executor.run(new TaskContext("0", job.getId()));
                        jobSuccess("");
                    } catch (Throwable throwable) {
                        log.error("[{}] run error", getClass().getSimpleName(), throwable);
                        jobFail(throwable.getMessage());
                    }
                }
            });
            // 提交状态监控
            this.statusReportFuture = workerContext.statusReportExecutor().scheduleAtFixedRate(
                this::report, 1, BrokerRemoteConstant.JOB_REPORT_SECONDS, TimeUnit.SECONDS
            );
            postProcessOnStart();
            return true;
        } catch (Exception e) {
            if (e instanceof RejectedExecutionException) {
                log.error("Schedule job in worker failed, maybe work thread exhausted job:{}", JacksonUtils.toJSONString(job), e);
            } else {
                log.error("Schedule job in worker failed, job:{}", JacksonUtils.toJSONString(job), e);
            }
            destroy();
            return false;
        }
    }

    protected void postProcessOnStart() {}

    protected abstract void run();

    protected void jobStart() {
        job.setStatus(JobStatus.RUNNING);
        statTransition(JobStateEvent.START);
    }

    protected void jobSuccess(String result) {
        job.setStatus(JobStatus.SUCCEED);
        job.setResult(result);
        statTransition(JobStateEvent.RUN_SUCCESS);
    }

    protected void jobFail(String errorMsg) {
        job.setStatus(JobStatus.FAILED);
        job.setErrorMsg(errorMsg);
        statTransition(JobStateEvent.RUN_FAIL);
    }

    protected void statTransition(JobStateEvent event) {
        JobStateTransitionRequest request = new JobStateTransitionRequest();
        try {
            JobMonitorDTO monitor = jobMonitor();
            request.setJobId(job.getId());
            request.setReportAt(TimeUtils.currentLocalDateTime());
            request.setWorkerNode(WorkerClientConverter.toDTO(workerContext.node()));
            request.setMonitor(monitor);
            request.setEvent(event.value);

            request.setResult(job.getResult());

            request.setErrorMsg(job.getErrorMsg());

            Response<JobStateTransitionResponse> response = workerContext.call(API_JOB_STATE_TRANSITION, request);
            if (response.success() && response.getData() != null && response.getData().isSuccess()) {
                return;
            }
            if (!job.getStatus().isFinished()) {
                destroy();
                return;
            }
            finishFailedCount++;
            if (finishFailedCount > MAX_FINISH_FAILED_TIMES) {
                log.warn("[JobFinish] fail more than {} times jobId:{} event:{}", MAX_FINISH_FAILED_TIMES, job.getId(), event);
                destroy();
                return;
            }
            // 启动定时尝试
            startFinishSchedule(event);
        } catch (Exception e) {
            log.error("[JobStateTransition] fail request={}", JacksonUtils.toJSONString(request), e);
            // 启动定时尝试
            startFinishSchedule(event);
        }
    }

    /**
     * 启动定时尝试
     * @param event 状态事件
     */
    private void startFinishSchedule(JobStateEvent event) {
        this.finishReportFuture = workerContext.statusReportExecutor().scheduleAtFixedRate(
            () -> statTransition(event), 1, 2, TimeUnit.MINUTES
        );
    }

    protected void report() {
        if (destroyed.get() || job.getStatus().isFinished()) {
            return;
        }
        JobReportRequest request = new JobReportRequest();
        try {
            JobMonitorDTO monitor = jobMonitor();
            request.setJobId(job.getId());
            request.setReportAt(TimeUtils.currentLocalDateTime());
            request.setWorkerNode(WorkerClientConverter.toDTO(workerContext.node()));
            request.setMonitor(monitor);
            request.setStatus(job.getStatus().value);

            workerContext.call(API_JOB_REPORT, request);
        } catch (Exception e) {
            log.error("[JobReport] fail request={}", JacksonUtils.toJSONString(request), e);
        }
    }

    @Override
    public void destroy() {
        if (!destroyed.compareAndSet(false, true)) {
            log.info("JobTracker jobId: {} has been destroyed", job.getId());
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
        postProcessOnDestroy();
        workerContext.deleteJob(job.getId());
        log.info("JobTracker jobId: {} destroyed success", job.getId());
    }

    protected void postProcessOnDestroy() {}

    public Job job() {
        return job;
    }

    protected JobMonitorDTO jobMonitor() {
        return new JobMonitorDTO();
    }

}
