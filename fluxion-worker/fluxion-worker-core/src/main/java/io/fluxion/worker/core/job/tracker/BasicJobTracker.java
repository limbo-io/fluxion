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

    public BasicJobTracker(Job job, Executor executor, WorkerContext workerContext) {
        super(job, executor, workerContext);
    }

    @Override
    public void run() {
        try {
            // 反馈执行中 -- 排除由于网络问题导致的失败可能性
            boolean success = reportStart();
            if (!success) {
                // 不成功，可能已经下发给其它节点
                return;
            }

            Task task = new Task("0", job.getId());
            task.setStatus(TaskStatus.RUNNING);
            task.setRemoteAddress(workerContext.address());
            task.setWorkerAddress(workerContext.address());

            taskCounter.getTotal().set(1);

            // 执行
            executor.run(task);

            // 执行成功
            taskCounter.getSuccess().incrementAndGet();
            reportSuccess();
        } catch (Throwable throwable) {
            log.error("[{}] run error", getClass().getSimpleName(), throwable);
            taskCounter.getFail().incrementAndGet();
            reportFail(throwable.getMessage());
        } finally {
            destroy();
        }
    }

}
