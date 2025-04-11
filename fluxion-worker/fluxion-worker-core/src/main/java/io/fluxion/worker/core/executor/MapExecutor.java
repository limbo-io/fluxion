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

package io.fluxion.worker.core.executor;

import io.fluxion.worker.core.job.Job;
import io.fluxion.worker.core.task.Task;

import java.util.List;

/**
 * @author Devil
 */
public abstract class MapExecutor implements Executor {

    /**
     * 切分创建多个子task
     *
     * @param job 任务
     */
    public abstract List<Task> sharding(Job job);
}
