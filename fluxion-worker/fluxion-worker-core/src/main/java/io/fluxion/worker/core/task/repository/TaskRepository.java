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

import io.fluxion.remote.core.api.request.TaskPageRequest;
import io.fluxion.remote.core.constants.ExecuteMode;
import io.fluxion.remote.core.constants.TaskStatus;
import io.fluxion.worker.core.task.Task;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author Devil
 */
public interface TaskRepository {

    boolean batchSave(Collection<Task> tasks);

    Task getById(String jobId, String taskId);

    Set<String> getIdsByTaskIds(String jobId, Collection<String> taskIds);

    boolean deleteByJobId(String jobId);

    List<Task> getUnDispatched(String triggerAt, String startId, Integer limit);

    List<Task> getByLastReportBetween(String reportTimeStart, String reportTimeEnd, TaskStatus status, String taskId, Integer limit);

    List<Task> getByJobId(String jobId);

    List<Task> queryPage(TaskPageRequest request);

    long queryCount(TaskPageRequest request);

    List<String> getAllTaskResult(String jobId, ExecuteMode executeMode);

    boolean dispatched(String jobId, String taskId, String workerAddress);

    boolean dispatchFail(String jobId, String taskId);

    boolean start(Task task);

    boolean report(Task task);

    boolean success(Task task);

    boolean fail(Task task);
}
