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

package io.fluxion.worker.core.executor;


import io.fluxion.worker.core.task.Task;

/**
 * 任务执行器
 *
 * @author Devil
 * @since 2021/7/24
 */
public interface Executor {

    /**
     * 运行执行器
     *
     * @param task 任务执行上下文
     */
    void run(Task task);


    /**
     * 执行器名称，默认为执行器类的类全名
     */
    default String name() {
        return this.getClass().getName();
    }

}
