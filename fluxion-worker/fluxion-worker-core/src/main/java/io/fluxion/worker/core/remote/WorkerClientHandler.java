/*
 * Copyright 2025-2030 Limbo Team (https://github.com/limbo-io).
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

import io.fluxion.remote.core.api.Response;
import io.fluxion.remote.core.api.request.worker.JobDispatchRequest;
import io.fluxion.remote.core.api.request.worker.TaskDispatchRequest;
import io.fluxion.remote.core.api.request.worker.TaskReportRequest;
import io.fluxion.remote.core.api.request.worker.TaskStateTransitionRequest;
import io.fluxion.remote.core.api.response.worker.TaskReportResponse;
import io.fluxion.remote.core.api.response.worker.TaskStateTransitionResponse;
import io.fluxion.remote.core.client.server.ClientHandler;
import io.fluxion.remote.core.constants.WorkerRemoteConstant;
import io.fluxion.worker.core.WorkerContext;
import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.job.Job;
import io.fluxion.worker.core.job.tracker.*;
import io.fluxion.worker.core.task.Task;
import io.fluxion.worker.core.task.repository.TaskRepository;
import io.fluxion.worker.core.task.tracker.TaskTracker;
import io.limbo.utils.json.JacksonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author PengQ
 * @since 0.0.1
 */
public class WorkerClientHandler implements ClientHandler {

    private static final Logger log = LoggerFactory.getLogger(WorkerClientHandler.class);

    private final WorkerContext workerContext;

    private final TaskRepository taskRepository;

    public WorkerClientHandler(WorkerContext workerContext, TaskRepository taskRepository) {
        this.workerContext = workerContext;
        this.taskRepository = taskRepository;
    }

    @Override
    public Response<?> process(String path, String data) {
        try {
            switch (path) {
                case WorkerRemoteConstant.API_JOB_DISPATCH: {
                    return Response.ok(jobDispatch(data));
                }
                case WorkerRemoteConstant.API_TASK_DISPATCH: {
                    return Response.ok(taskReceive(data));
                }
                case WorkerRemoteConstant.API_TASK_REPORT: {
                    return Response.ok(taskReport(data));
                }
                case WorkerRemoteConstant.API_TASK_STATE_TRANSITION: {
                    return Response.ok(taskStateTransition(data));
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

    /**
     * 任务下发幂等缓存：key = jobId + dispatchAttempt
     */
    private final java.util.Map<DispatchCacheKey, DispatchCacheEntry> dispatchCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 缓存过期时间：10分钟
     */
    private static final long DISPATCH_CACHE_EXPIRE_MS = 10 * 60 * 1000;

    private boolean jobDispatch(String data) {
        JobDispatchRequest request = JacksonUtils.toType(data, JobDispatchRequest.class);

        // 幂等性检查：相同的(jobId, dispatchAttempt)直接返回缓存结果
        DispatchCacheKey cacheKey = new DispatchCacheKey(request.getJobId(), request.getDispatchAttempt());
        DispatchCacheEntry cached = dispatchCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.info("[WorkerClientHandler] Returning cached dispatch result for job {} attempt {}: success={}",
                request.getJobId(), request.getDispatchAttempt(), cached.isSuccess());
            return cached.isSuccess();
        }

        Job job = WorkerClientConverter.toJob(request);
        Executor executor = workerContext.executor(job.getExecutorName());
        if (executor == null) {
            throw new IllegalArgumentException("unknown executor name:" + job.getExecutorName());
        }
        if (!workerContext.status().isRunning()) {
            log.info("Worker is not running: {}", workerContext.status());
            cacheDispatchResult(cacheKey, false);
            return false;
        }
        JobTracker tracker;
        switch (job.getExecuteMode()) {
            case STANDALONE:
                tracker = new BasicJobTracker(job, executor, workerContext);
                break;
            case BROADCAST:
                tracker = new BroadcastJobTracker(job, executor, workerContext, taskRepository);
                break;
            case MAP:
            case MAP_REDUCE:
                tracker = new MapReduceJobTracker(job, executor, workerContext, taskRepository);
                break;
            default:
                throw new IllegalArgumentException("unknown execute mode:" + job.getExecuteMode());
        }
        if (!workerContext.saveJobTracker(tracker)) {
            log.info("Receive job [{}], but already in repository", job.getId());
            cacheDispatchResult(cacheKey, true);
            return true;
        }
        boolean success = tracker.start();
        cacheDispatchResult(cacheKey, success);
        return success;
    }

    private void cacheDispatchResult(DispatchCacheKey key, boolean success) {
        dispatchCache.put(key, new DispatchCacheEntry(success, System.currentTimeMillis()));
        // 简单清理过期条目（实际生产环境应使用带过期时间的缓存）
        cleanupExpiredCacheEntries();
    }

    private void cleanupExpiredCacheEntries() {
        long now = System.currentTimeMillis();
        dispatchCache.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    /**
     * 幂等缓存键
     */
    private static class DispatchCacheKey {
        private final String jobId;
        private final int dispatchAttempt;

        DispatchCacheKey(String jobId, int dispatchAttempt) {
            this.jobId = jobId;
            this.dispatchAttempt = dispatchAttempt;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DispatchCacheKey that = (DispatchCacheKey) o;
            return dispatchAttempt == that.dispatchAttempt && java.util.Objects.equals(jobId, that.jobId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(jobId, dispatchAttempt);
        }
    }

    /**
     * 幂等缓存条目
     */
    private static class DispatchCacheEntry {
        private final boolean success;
        private final long timestamp;

        DispatchCacheEntry(boolean success, long timestamp) {
            this.success = success;
            this.timestamp = timestamp;
        }

        boolean isSuccess() { return success; }

        boolean isExpired() { return isExpired(System.currentTimeMillis()); }

        boolean isExpired(long now) { return now - timestamp > DISPATCH_CACHE_EXPIRE_MS; }
    }

    /**
     * 接收worker下发的task
     */
    private boolean taskReceive(String data) {
        TaskDispatchRequest request = JacksonUtils.toType(data, TaskDispatchRequest.class);
        Executor executor = workerContext.executor(request.getExecutorName());
        if (executor == null) {
            throw new IllegalArgumentException("unknown executor name:" + request.getExecutorName());
        }
        Task task = WorkerClientConverter.toTask(request, workerContext);
        TaskTracker tracker = new TaskTracker(task, executor, workerContext);
        return tracker.start();
    }

    private TaskReportResponse taskReport(String data) {
        TaskReportRequest request = JacksonUtils.toType(data, TaskReportRequest.class);
        JobTracker tracker = workerContext.getJobTracker(request.getJobId());
        if (tracker == null) {
            log.error("[TaskReport] not found tracker request:{}", request);
            return new TaskReportResponse();
        }
        if (tracker instanceof DistributedJobTracker) {
            return ((DistributedJobTracker) tracker).handleTaskReport(request);
        }
        log.error("[TaskReport] tracker type error request:{}", request);
        return new TaskReportResponse();
    }

    private TaskStateTransitionResponse taskStateTransition(String data) {
        TaskStateTransitionRequest request = JacksonUtils.toType(data, TaskStateTransitionRequest.class);
        JobTracker tracker = workerContext.getJobTracker(request.getJobId());
        if (tracker == null) {
            log.error("[TaskStateTransition] not found tracker request:{}", request);
            return new TaskStateTransitionResponse();
        }
        if (tracker instanceof DistributedJobTracker) {
            return ((DistributedJobTracker) tracker).handleTaskStateTransitionRequest(request);
        }
        log.error("[TaskStateTransition] tracker type error request:{}", request);
        return new TaskStateTransitionResponse();
    }

}
