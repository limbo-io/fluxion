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

import io.fluxion.remote.core.api.dto.TaskMonitorDTO;
import io.fluxion.worker.core.WorkerContext;
import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.job.Job;
import io.fluxion.worker.core.job.TaskCounter;
import io.fluxion.worker.core.task.Task;

/**
 * @author Devil
 */
public abstract class DistributedJobTracker extends JobTracker {

    protected final TaskCounter taskCounter;

    public DistributedJobTracker(Job job, Executor executor, WorkerContext workerContext) {
        super(job, executor, workerContext);
        this.taskCounter = new TaskCounter();
    }

    public abstract void success(Task task);

    public abstract void fail(Task task);

    @Override
    public void report() {
        reportJob(taskMonitor());
    }

    protected TaskMonitorDTO taskMonitor() {
        TaskMonitorDTO dto = new TaskMonitorDTO();
        dto.setTotal(taskCounter.getTotal().get());
        dto.setSuccess(taskCounter.getSuccess().get());
        dto.setFail(taskCounter.getFail().get());
        return dto;
    }

}
