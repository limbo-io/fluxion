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

import io.fluxion.worker.core.rpc.TaskRpc;
import io.fluxion.worker.core.task.Task;

/**
 * @author Devil
 */
public abstract class TaskTracker {

    /**
     * 从 Broker 接收到的任务
     */
    protected final Task task;

    /**
     * Rpc
     */
    protected final TaskRpc rpc;

    public TaskTracker(Task task, TaskRpc rpc) {
        this.task = task;
        this.rpc = rpc;
    }

}
