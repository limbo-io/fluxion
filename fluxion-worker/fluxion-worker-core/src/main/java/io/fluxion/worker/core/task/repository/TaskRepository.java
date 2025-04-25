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

package io.fluxion.worker.core.task.repository;

import io.fluxion.remote.core.constants.TaskStatus;
import io.fluxion.worker.core.task.Task;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

/**
 * @author Devil
 */
public interface TaskRepository {

    boolean batchSave(Collection<Task> tasks);

    Task getById(String jobId, String taskId);

    Map<String, String> getAllSubTaskResult(String jobId);

    /**
     * create -> dispatched
     */
    boolean dispatched(String jobId, String taskId, String workerAddress);

    /**
     * dispatched -> running
     */
    boolean start(String jobId, String taskId, String workerAddress, LocalDateTime reportAt);

    /**
     * 修改report时间
     *
     * dispatched
     * 执行/上报两个线程，上报线程先执行
     *
     * running
     */
    boolean report(String jobId, String taskId, TaskStatus status, String workerAddress, LocalDateTime reportTime);

    /**
     * running -> success
     */
    boolean success(Task task);

    /**
     * created -> fail
     * 下发失败
     *
     * running -> fail
     * 运行失败
     */
    boolean fail(Task task);
}
