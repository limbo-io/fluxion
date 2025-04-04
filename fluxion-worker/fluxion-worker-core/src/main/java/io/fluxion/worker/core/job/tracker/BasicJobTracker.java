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

import io.fluxion.remote.core.constants.TaskStatus;
import io.fluxion.worker.core.WorkerContext;
import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.job.Job;
import io.fluxion.worker.core.task.Task;

/**
 * 执行一个任务
 *
 * @author Devil
 */
public class BasicJobTracker extends JobTracker {

    private final Executor executor;

    public BasicJobTracker(Job job, Executor executor, WorkerContext workerContext) {
        super(job, workerContext);
        this.executor = executor;
    }

    @Override
    public void run() {
        Task task = new Task("0", job.getId());
        task.setStatus(TaskStatus.RUNNING);
        task.setRemoteAddress(workerContext.address());
        task.setWorkerAddress(workerContext.address());
        executor.run(task);

        // 执行成功
        reportSuccess();
    }

}
