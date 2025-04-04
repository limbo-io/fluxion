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

package io.fluxion.worker.core.remote;

import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.remote.core.api.Response;
import io.fluxion.remote.core.api.request.JobDispatchRequest;
import io.fluxion.remote.core.api.request.TaskDispatchRequest;
import io.fluxion.remote.core.api.request.TaskDispatchedRequest;
import io.fluxion.remote.core.api.request.TaskFailRequest;
import io.fluxion.remote.core.api.request.TaskReportRequest;
import io.fluxion.remote.core.api.request.TaskStartRequest;
import io.fluxion.remote.core.api.request.TaskSuccessRequest;
import io.fluxion.remote.core.client.server.ClientHandler;
import io.fluxion.remote.core.constants.WorkerRemoteConstant;
import io.fluxion.worker.core.WorkerContext;
import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.job.Job;
import io.fluxion.worker.core.job.tracker.BasicJobTracker;
import io.fluxion.worker.core.job.tracker.BroadcastJobTracker;
import io.fluxion.worker.core.job.tracker.JobTracker;
import io.fluxion.worker.core.job.tracker.MapReduceJobTracker;
import io.fluxion.worker.core.task.Task;
import io.fluxion.worker.core.task.repository.TaskRepository;
import io.fluxion.worker.core.task.tracker.TaskTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author PengQ
 * @since 0.0.1
 */
public class WorkerClientHandler implements ClientHandler {

    private static final Logger log = LoggerFactory.getLogger(WorkerClientHandler.class);

    private final WorkerContext workerContext;

    public WorkerClientHandler(WorkerContext workerContext) {
        this.workerContext = workerContext;
    }

    @Override
    public Response<?> process(String path, String data) {
        try {
            switch (path) {
                case WorkerRemoteConstant.API_JOB_DISPATCH: {
                    return Response.ok(jobDispatch(data));
                }
                case WorkerRemoteConstant.API_TASK_DISPATCH: {
                    return Response.ok(taskDispatch(data));
                }
                case WorkerRemoteConstant.API_TASK_DISPATCHED: {
                    return Response.ok(taskDispatched(data));
                }
                case WorkerRemoteConstant.API_TASK_START: {
                    return Response.ok(taskStart(data));
                }
                case WorkerRemoteConstant.API_TASK_REPORT: {
                    return Response.ok(taskReport(data));
                }
                case WorkerRemoteConstant.API_TASK_SUCCESS: {
                    return Response.ok(taskSuccess(data));
                }
                case WorkerRemoteConstant.API_TASK_FAIL: {
                    return Response.ok(taskFail(data));
                }
            }
            String msg = "Invalid request, Path NotFound.";
            log.info("{} path={}", msg, path);
            return Response.builder().notFound(msg).build();
        } catch (Exception e) {
            log.error("Request process error path={} data={}", path, data, e);
            return Response.builder().error(e.getMessage()).build();
        }
    }

    private boolean jobDispatch(String data) {
        JobDispatchRequest request = JacksonUtils.toType(data, JobDispatchRequest.class);
        Job job = WorkerClientConverter.toJob(request);
        Executor executor = workerContext.executor(job.getExecutorName());
        if (executor == null) {
            throw new IllegalArgumentException("unknown executor name:" + job.getExecutorName());
        }
        JobTracker tracker;
        switch (job.getExecuteMode()) {
            case STANDALONE:
                tracker = new BasicJobTracker(job, executor, workerContext);
                break;
            case BROADCAST:
                tracker = new BroadcastJobTracker(job, executor, workerContext);
                break;
            case MAP:
            case MAP_REDUCE:
                tracker = new MapReduceJobTracker(job, executor, workerContext);
                break;
            default:
                throw new IllegalArgumentException("unknown execute mode:" + job.getExecuteMode());
        }
        return tracker.start();
    }

    private boolean taskDispatch(String data) {
        TaskDispatchRequest request = JacksonUtils.toType(data, TaskDispatchRequest.class);
        Executor executor = workerContext.executor(request.getExecutorName());
        if (executor == null) {
            throw new IllegalArgumentException("unknown executor name:" + request.getExecutorName());
        }
        Task task = WorkerClientConverter.toTask(request, workerContext);
        TaskTracker tracker = new TaskTracker(task, executor, workerContext);
        return tracker.start();
    }

    private boolean taskDispatched(String data) {
        TaskDispatchedRequest request = JacksonUtils.toType(data, TaskDispatchedRequest.class);
        return taskRepository().dispatched(
            request.getJobId(), request.getTaskId(),
            request.getWorkerAddress()
        );
    }

    private boolean taskStart(String data) {
        TaskStartRequest request = JacksonUtils.toType(data, TaskStartRequest.class);
        Task task = taskRepository().getById(request.getJobId(), request.getTaskId());
        if (!task.getWorkerAddress().equals(request.getWorkerAddress())) {
            return false;
        }
        task.setLastReportAt(request.getReportAt());
        return taskRepository().start(task);
    }

    private boolean taskReport(String data) {
        TaskReportRequest request = JacksonUtils.toType(data, TaskReportRequest.class);
        Task task = taskRepository().getById(request.getJobId(), request.getTaskId());
        if (!task.getWorkerAddress().equals(request.getWorkerAddress())) {
            return false;
        }
        task.setLastReportAt(request.getReportAt());
        return taskRepository().report(task);
    }

    private boolean taskSuccess(String data) {
        TaskSuccessRequest request = JacksonUtils.toType(data, TaskSuccessRequest.class);
        Task task = taskRepository().getById(request.getJobId(), request.getTaskId());
        if (!task.getWorkerAddress().equals(request.getWorkerAddress())) {
            return false;
        }
        task.setLastReportAt(request.getReportAt());
        task.setResult(request.getResult());
        boolean updated = taskRepository().success(task);
        if (!updated) {
            return false;
        }
        JobTracker tracker = workerContext.getJob(request.getJobId());
        tracker.success(task);
        return true;
    }

    private boolean taskFail(String data) {
        TaskFailRequest request = JacksonUtils.toType(data, TaskFailRequest.class);
        Task task = taskRepository().getById(request.getJobId(), request.getTaskId());
        if (!task.getWorkerAddress().equals(request.getWorkerAddress())) {
            return false;
        }
        task.setLastReportAt(request.getReportAt());
        task.setErrorMsg(request.getErrorMsg());
        task.setErrorStackTrace(request.getErrorStackTrace());
        boolean updated = taskRepository().fail(task);
        if (!updated) {
            return false;
        }
        JobTracker tracker = workerContext.getJob(request.getJobId());
        tracker.fail(task);
        return true;
    }

    private TaskRepository taskRepository() {
        return workerContext.taskRepository();
    }

}
