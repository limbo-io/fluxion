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

import io.fluxion.remote.core.api.constants.ExecuteType;
import io.fluxion.remote.core.api.request.TaskDispatchRequest;
import io.fluxion.worker.core.task.Task;

/**
 * @author PengQ
 * @since 0.0.1
 */
public class WorkerClientConverter {

    public static Task toTask(TaskDispatchRequest request) {
        Task task = new Task();
        task.setTaskId(request.getTaskId());
        task.setBrokerId(request.getBrokerId());
        task.setExecuteType(ExecuteType.parse(request.getExecuteType()));
        task.setExecutorName(request.getExecutorName());
        return task;
    }
}
