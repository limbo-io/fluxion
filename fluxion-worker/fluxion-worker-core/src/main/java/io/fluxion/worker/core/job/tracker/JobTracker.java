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
import io.fluxion.remote.core.api.dto.TaskMonitorDTO;
import io.fluxion.remote.core.api.request.JobDispatchedRequest;
import io.fluxion.remote.core.api.request.JobFailRequest;
import io.fluxion.remote.core.api.request.JobReportRequest;
import io.fluxion.remote.core.api.request.JobStartRequest;
import io.fluxion.remote.core.api.request.JobSuccessRequest;
import io.fluxion.remote.core.constants.BrokerRemoteConstant;
import io.fluxion.worker.core.AbstractTracker;
import io.fluxion.worker.core.WorkerContext;
import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.job.Job;
import io.fluxion.worker.core.job.TaskCounter;

import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.fluxion.remote.core.constants.BrokerRemoteConstant.API_JOB_DISPATCHED;
import static io.fluxion.remote.core.constants.BrokerRemoteConstant.API_JOB_FAIL;
import static io.fluxion.remote.core.constants.BrokerRemoteConstant.API_JOB_REPORT;
import static io.fluxion.remote.core.constants.BrokerRemoteConstant.API_JOB_START;
import static io.fluxion.remote.core.constants.BrokerRemoteConstant.API_JOB_SUCCESS;

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

    protected final Executor executor;

    protected final WorkerContext workerContext;

    protected final AtomicBoolean destroyed;

    protected Future<?> processFuture;

    protected Future<?> statusReportFuture;

    protected final TaskCounter taskCounter;

    public JobTracker(Job job, Executor executor, WorkerContext workerContext) {
        this.job = job;
        this.executor = executor;
        this.workerContext = workerContext;
        this.destroyed = new AtomicBoolean(false);
        this.taskCounter = new TaskCounter();
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
            this.processFuture = workerContext.taskProcessExecutor().submit(this::run);
            // 提交状态监控
            this.statusReportFuture = workerContext.taskStatusReportExecutor().scheduleAtFixedRate(
                this::report, 1, BrokerRemoteConstant.JOB_REPORT_SECONDS, TimeUnit.SECONDS
            );
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

    public void report() {

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
        workerContext.deleteJob(job.getId());
        log.info("JobTracker jobId: {} destroyed success", job.getId());
    }

    protected boolean reportDispatched() {
        try {
            JobDispatchedRequest request = new JobDispatchedRequest();
            request.setJobId(job.getId());
            request.setWorkerAddress(workerContext.node().address());
            Response<Boolean> response = workerContext.call(API_JOB_DISPATCHED, request);
            if (!response.success()) {
                return false;
            }
            return response.getData();
        } catch (Exception e) {
            log.error("reportDispatched fail jobId={}", job.getId(), e);
            return false;
        }
    }

    protected boolean reportStart() {
        try {
            JobStartRequest request = new JobStartRequest();
            request.setJobId(job.getId());
            request.setWorkerAddress(workerContext.node().address());
            request.setReportAt(TimeUtils.currentLocalDateTime());
            Response<Boolean> response = workerContext.call(API_JOB_START, request);
            if (!response.success()) {
                return false;
            }
            return response.getData();
        } catch (Exception e) {
            log.error("reportStart fail jobId={}", job.getId(), e);
            return false;
        }
    }

    protected boolean reportJob() {
        TaskMonitorDTO taskMonitor = taskMonitor();
        JobReportRequest request = new JobReportRequest();
        request.setJobId(job.getId());
        request.setReportAt(TimeUtils.currentLocalDateTime());
        request.setWorkerAddress(workerContext.node().address());
        request.setTaskMonitor(taskMonitor);
        Response<Boolean> response = workerContext.call(API_JOB_REPORT, request);
        if (!response.success()) {
            return false;
        }
        return response.getData();
    }

    protected void reportSuccess() {
        try {
            TaskMonitorDTO taskMonitor = taskMonitor();
            JobSuccessRequest request = new JobSuccessRequest();
            request.setJobId(job.getId());
            request.setWorkerAddress(workerContext.node().address());
            request.setReportAt(TimeUtils.currentLocalDateTime());
            request.setTaskMonitor(taskMonitor);
            workerContext.call(API_JOB_SUCCESS, request); // todo @d later 如果上报失败需要记录，定时重试
        } catch (Exception e) {
            log.error("reportSuccess fail jobId={}", job.getId(), e);
            // todo @d later 如果上报失败需要记录，定时重试
        }
    }

    protected void reportFail(String errorMsg) {
        try {
            TaskMonitorDTO taskMonitor = taskMonitor();
            JobFailRequest request = new JobFailRequest();
            request.setJobId(job.getId());
            request.setWorkerAddress(workerContext.node().address());
            request.setReportAt(TimeUtils.currentLocalDateTime());
            request.setErrorMsg(errorMsg);
            request.setTaskMonitor(taskMonitor);
            workerContext.call(API_JOB_FAIL, request); // todo @d later 如果上报失败需要记录，定时重试
        } catch (Exception e) {
            log.error("reportFail fail jobId={}", job.getId(), e);
            // todo @d later 如果上报失败需要记录，定时重试
        }
    }

    public Job job() {
        return job;
    }

    private TaskMonitorDTO taskMonitor() {
        TaskMonitorDTO dto = new TaskMonitorDTO();
        dto.setTotalNum(taskCounter.getTotal().get());
        dto.setSuccessNum(taskCounter.getSuccess().get());
        dto.setFailNum(taskCounter.getFail().get());
        return dto;
    }

}
