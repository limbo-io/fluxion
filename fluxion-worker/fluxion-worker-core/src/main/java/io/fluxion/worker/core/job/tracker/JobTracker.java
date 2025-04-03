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
import io.fluxion.remote.core.api.request.JobDispatchedRequest;
import io.fluxion.remote.core.api.request.JobFeedbackRequest;
import io.fluxion.remote.core.api.request.JobReportRequest;
import io.fluxion.remote.core.api.request.JobStartRequest;
import io.fluxion.remote.core.constants.BrokerRemoteConstant;
import io.fluxion.remote.core.constants.ExecuteResult;
import io.fluxion.worker.core.AbstractTracker;
import io.fluxion.worker.core.WorkerContext;
import io.fluxion.worker.core.job.Job;

import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.fluxion.remote.core.constants.BrokerRemoteConstant.API_JOB_DISPATCHED;
import static io.fluxion.remote.core.constants.BrokerRemoteConstant.API_JOB_FEEDBACK;
import static io.fluxion.remote.core.constants.BrokerRemoteConstant.API_JOB_REPORT;
import static io.fluxion.remote.core.constants.BrokerRemoteConstant.API_JOB_START;

/**
 * Task执行管理和监控
 *
 * @author Devil
 */
public abstract class JobTracker extends AbstractTracker {

    /**
     * 从 Broker 接收到的任务
     */
    protected final Job job;

    protected final WorkerContext workerContext;

    protected final AtomicBoolean destroyed;

    protected Future<?> processFuture;

    protected Future<?> statusReportFuture;

    public JobTracker(Job job, WorkerContext workerContext) {
        this.job = job;
        this.workerContext = workerContext;
        this.destroyed = new AtomicBoolean(false);
    }

    @Override
    public boolean start() {
        if (!workerContext.status().isRunning()) {
            log.info("Worker is not running: {}", workerContext.status());
            return false;
        }
        if (!workerContext.saveJob(this)) {
            log.info("Receive job [{}], but already in repository", job.getId());
            return true;
        }

        try {
            if (!reportDispatched()) {
                destroy();
                return true;
            }
            // 提交执行 正常来说保存成功这里不会被拒绝
            this.processFuture = workerContext.taskProcessExecutor().submit(() -> {
                try {
                    // 反馈执行中 -- 排除由于网络问题导致的失败可能性
                    boolean success = reportStart();
                    if (!success) {
                        // 不成功，可能已经下发给其它节点
                        return;
                    }
                    run();
                } catch (Throwable throwable) {
                    log.error("[{}] run error", getClass().getSimpleName(), throwable);
                    reportFail(throwable.getMessage());
                } finally {
                    destroy();
                }
            });
            // 提交状态监控
            this.statusReportFuture = workerContext.taskStatusReportExecutor().scheduleAtFixedRate(() -> {
                JobReportRequest request = new JobReportRequest();
                request.setJobId(job.getId());
                request.setReportAt(TimeUtils.currentLocalDateTime());
                request.setWorkerAddress(workerContext.address());
                workerContext.call(API_JOB_REPORT, request);
            }, 1, BrokerRemoteConstant.JOB_REPORT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (RejectedExecutionException e) {
            log.error("Schedule job in worker failed, maybe work thread exhausted job:{}", JacksonUtils.toJSONString(job), e);
            destroy();
            return false;
        } catch (Exception e) {
            log.error("Schedule job in worker failed, job:{}", JacksonUtils.toJSONString(job), e);
            destroy();
            return false;
        }
    }

    public abstract void run();

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
        workerContext.deleteJob(job.getId());
        log.info("JobTracker jobId: {} destroyed success", job.getId());
    }

    protected boolean reportDispatched() {
        try {
            JobDispatchedRequest request = new JobDispatchedRequest();
            request.setJobId(job.getId());
            request.setWorkerAddress(workerContext.address());
            return workerContext.call(API_JOB_DISPATCHED, request);
        } catch (Exception e) {
            log.error("reportDispatched fail jobId={}", job.getId(), e);
            return false;
        }
    }

    protected boolean reportStart() {
        try {
            JobStartRequest request = new JobStartRequest();
            request.setJobId(job.getId());
            request.setWorkerAddress(workerContext.address());
            request.setReportAt(TimeUtils.currentLocalDateTime());
            return workerContext.call(API_JOB_START, request);
        } catch (Exception e) {
            log.error("reportStart fail jobId={}", job.getId(), e);
            return false;
        }
    }

    protected void reportSuccess() {
        try {
            JobFeedbackRequest request = new JobFeedbackRequest(
                job.getId(), workerContext.address(),
                TimeUtils.currentLocalDateTime(), ExecuteResult.SUCCEED.result
            );
            workerContext.call(API_JOB_FEEDBACK, request); // todo @d later 如果上报失败需要记录，定时重试
        } catch (Exception e) {
            log.error("reportSuccess fail jobId={}", job.getId(), e);
            // todo @d later 如果上报失败需要记录，定时重试
        }
    }

    protected void reportFail(String errorMsg) {
        try {
            JobFeedbackRequest request = new JobFeedbackRequest(
                job.getId(), workerContext.address(),
                TimeUtils.currentLocalDateTime(), ExecuteResult.FAILED.result, errorMsg
            );
            workerContext.call(API_JOB_FEEDBACK, request); // todo @d later 如果上报失败需要记录，定时重试
        } catch (Exception e) {
            log.error("reportFail fail jobId={}", job.getId(), e);
            // todo @d later 如果上报失败需要记录，定时重试
        }
    }

    public Job job() {
        return job;
    }

}
