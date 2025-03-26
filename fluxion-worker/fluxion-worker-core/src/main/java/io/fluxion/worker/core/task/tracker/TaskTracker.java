/*
 * Copyright 2025-2030 fluxion-io Team (https://github.com/fluxion-io).
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
import io.fluxion.remote.core.api.request.broker.TaskFailRequest;
import io.fluxion.remote.core.api.request.broker.TaskStartRequest;
import io.fluxion.remote.core.api.request.broker.TaskSuccessRequest;
import io.fluxion.worker.core.WorkerContext;
import io.fluxion.worker.core.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.fluxion.remote.core.constants.BrokerRemoteConstant.API_TASK_FAIL;
import static io.fluxion.remote.core.constants.BrokerRemoteConstant.API_TASK_START;
import static io.fluxion.remote.core.constants.BrokerRemoteConstant.API_TASK_SUCCESS;

/**
 * Task执行管理和监控
 *
 * @author Devil
 */
public abstract class TaskTracker {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * 从 Broker 接收到的任务
     */
    protected final Task task;

    protected final WorkerContext workerContext;

    protected final AtomicBoolean destroyed;

    protected Future<?> processFuture;

    protected Future<?> statusReportFuture;

    public TaskTracker(Task task, WorkerContext workerContext) {
        this.task = task;
        this.workerContext = workerContext;
        this.destroyed = new AtomicBoolean(false);
    }

    public Task task() {
        return task;
    }

    public boolean start() {
        if (!workerContext.status().isRunning()) {
            log.info("Worker is not running: {}", workerContext.status());
            return false;
        }

        if (!workerContext.saveTask(this)) {
            log.info("Receive task [{}], but already in repository", task.getTaskId());
            return false;
        }

        try {
            run();
            return true;
        } catch (RejectedExecutionException e) {
            log.error("Schedule task in worker failed, maybe work thread exhausted task:{}", JacksonUtils.toJSONString(task), e);
            destroy();
            return false;
        } catch (Exception e) {
            log.error("Schedule task in worker failed, task:{}", JacksonUtils.toJSONString(task), e);
            destroy();
            return false;
        }
    }

    public abstract void run();

    public void destroy() {
        if (!destroyed.compareAndSet(false, true)) {
            log.info("TaskTracker taskId: {} has been destroyed", task.getTaskId());
            return;
        }
        if (statusReportFuture != null) {
            statusReportFuture.cancel(true);
        }
        if (processFuture != null) {
            processFuture.cancel(true);
        }
        workerContext.removeTask(task.getTaskId());
        log.info("TaskTracker taskId: {} destroyed success", task.getTaskId());
    }

    protected boolean reportStart(Task task) {
        try {
            TaskStartRequest request = new TaskStartRequest();
            request.setTaskId(task.getTaskId());
            request.setWorkerAddress(workerContext.address());
            request.setReportAt(TimeUtils.currentLocalDateTime());
            return workerContext.call(API_TASK_START, request);
        } catch (Exception e) {
            log.error("reportStart fail task={}", task.getTaskId(), e);
            return false;
        }
    }

    protected void reportSuccess(Task task) {
        try {
            TaskSuccessRequest request = new TaskSuccessRequest();
            request.setTaskId(task.getTaskId());
            request.setReportAt(TimeUtils.currentLocalDateTime());
            request.setWorkerAddress(workerContext.address());
            workerContext.call(API_TASK_SUCCESS, request); // todo @d later 如果上报失败需要记录，定时重试
        } catch (Exception e) {
            log.error("reportSuccess fail task={}", task.getTaskId(), e);
            // todo @d later 如果上报失败需要记录，定时重试
        }
    }

    protected void reportFail(Task task, Throwable throwable) {
        try {
            TaskFailRequest request = new TaskFailRequest();
            request.setTaskId(task.getTaskId());
            request.setReportAt(TimeUtils.currentLocalDateTime());
            request.setWorkerAddress(workerContext.address());
            request.setErrorMsg(throwable.getMessage());
            workerContext.call(API_TASK_FAIL, request); // todo @d later 如果上报失败需要记录，定时重试
        } catch (Exception e) {
            log.error("reportFail fail task={}", task.getTaskId(), e);
            // todo @d later 如果上报失败需要记录，定时重试
        }
    }

}
