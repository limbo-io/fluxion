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

import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.remote.core.constants.TaskStatus;
import io.fluxion.worker.core.WorkerContext;
import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.job.Job;
import io.fluxion.worker.core.task.Task;
import io.fluxion.worker.core.task.TaskContext;
import io.fluxion.worker.core.task.repository.TaskRepository;

import java.time.LocalDateTime;
import java.util.Collections;

/**
 * 执行一个任务
 *
 * @author Devil
 */
public class BasicJobTracker extends JobTracker {

    public BasicJobTracker(Job job, Executor executor,  WorkerContext workerContext, TaskRepository taskRepository) {
        super(job, executor, workerContext, taskRepository);
    }

    @Override
    public void run() {
        Task task = new Task("0", job.getId());
        task.setStatus(TaskStatus.RUNNING);
        task.setWorkerNode(workerContext.node());
        task.setRemoteNode(workerContext.node());
        LocalDateTime startTime = TimeUtils.currentLocalDateTime();
        task.setStartAt(startTime);
        task.setLastReportAt(startTime);

        taskCounter.getTotal().set(1);
        // 保存
        taskRepository.batchSave(Collections.singletonList(task));

        try {
            // 执行
            executor.run(TaskContext.from(task));
            // 执行成功
            LocalDateTime endTime = TimeUtils.currentLocalDateTime();
            task.setStatus(TaskStatus.SUCCEED);
            task.setEndAt(endTime);
            task.setLastReportAt(endTime);
            taskRepository.success(task);
            taskCounter.getSuccess().incrementAndGet();
            jobSuccess("");
        } catch (Exception e) {
            LocalDateTime endTime = TimeUtils.currentLocalDateTime();
            task.setStatus(TaskStatus.FAILED);
            task.setEndAt(endTime);
            task.setLastReportAt(endTime);
            taskRepository.fail(task);
            throw new RuntimeException(e);
        }
    }

}
