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

package io.fluxion.worker.core.rpc;

import io.fluxion.worker.core.task.Task;

import javax.annotation.Nullable;

/**
 * @author Devil
 */
public interface TaskRpc {

    /**
     * 反馈任务开始执行
     */
    Boolean reportTaskExecuting(Task task);

    /**
     * 反馈任务执行状态
     */
    Boolean reportTask(Task task);

    /**
     * 反馈任务执行成功
     */
    Boolean feedbackTaskSucceed(Task task);


    /**
     * 反馈任务执行失败
     * @param ex 导致任务失败的异常信息，可以为 null
     */
    Boolean feedbackTaskFailed(Task task, @Nullable Throwable ex);
}
