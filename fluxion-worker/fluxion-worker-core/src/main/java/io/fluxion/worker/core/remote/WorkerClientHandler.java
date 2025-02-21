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
import io.fluxion.remote.core.api.request.TaskDispatchRequest;
import io.fluxion.remote.core.client.server.ClientHandler;
import io.fluxion.remote.core.constants.WorkerConstant;
import io.fluxion.worker.core.WorkerContext;
import io.fluxion.worker.core.executor.BroadcastExecutor;
import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.executor.MapReduceExecutor;
import io.fluxion.worker.core.executor.StandaloneExecutor;
import io.fluxion.worker.core.task.Task;
import io.fluxion.worker.core.task.tracker.BasicTaskTracker;
import io.fluxion.worker.core.task.tracker.SubTaskTracker;
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
                case WorkerConstant.API_TASK_DISPATCH: {
                    TaskDispatchRequest request = JacksonUtils.toType(data, TaskDispatchRequest.class);
                    Task task = WorkerClientConverter.toTask(request);
                    TaskTracker tracker = createTaskTracker(task);
                    boolean success = tracker.start();
                    return Response.ok(success);
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

    private TaskTracker createTaskTracker(Task task) {
        Executor executor = workerContext.executor(task.getExecutorName());
        if (executor == null) {
            throw new IllegalArgumentException("unknown executor name:" + task.getExecutorName());
        }
        switch (task.getExecuteType()) {
            case STANDALONE:
                if (!(executor instanceof StandaloneExecutor)) {
                    throw new IllegalArgumentException("unknown executor:" + executor.name() + " not match executeType:" + task.getExecuteType().name());
                }
                return new BasicTaskTracker(task, executor, workerContext);
            case BROADCAST:
                if (!(executor instanceof BroadcastExecutor)) {
                    throw new IllegalArgumentException("unknown executor:" + executor.name() + " not match executeType:" + task.getExecuteType().name());
                }
                return new SubTaskTracker(task, executor, workerContext);
            case MAP:
            case MAP_REDUCE:
                if (!(executor instanceof MapReduceExecutor)) {
                    throw new IllegalArgumentException("unknown executor:" + executor.name() + " not match executeType:" + task.getExecuteType().name());
                }
                return new SubTaskTracker(task, executor, workerContext);
            default:
                throw new IllegalArgumentException("unknown execute type:" + task.getExecuteType().name());
        }
    }

}
