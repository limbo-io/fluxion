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

package io.fluxion.worker.core.remote;

import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.remote.core.api.Response;
import io.fluxion.remote.core.api.request.TaskDispatchRequest;
import io.fluxion.remote.core.client.server.ClientHandler;
import io.fluxion.remote.core.constants.WorkerConstant;
import io.fluxion.worker.core.task.TaskManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author PengQ
 * @since 0.0.1
 */
public class WorkerClientHandler implements ClientHandler {

    private static final Logger log = LoggerFactory.getLogger(WorkerClientHandler.class);

    private final TaskManager taskManager;

    public WorkerClientHandler(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Override
    public Response<?> process(String path, String data) {
        switch (path) {
            case WorkerConstant.API_TASK_DISPATCH: {
                TaskDispatchRequest request = JacksonUtils.toType(data, TaskDispatchRequest.class);
                boolean success = taskManager.receive(null); // todo @pq
                return Response.ok(success);
            }
        }
        String msg = "Invalid request, Path NotFound.";
        log.info("{} path={}", msg, path);
        return Response.builder().notFound(msg).build();
    }

}
