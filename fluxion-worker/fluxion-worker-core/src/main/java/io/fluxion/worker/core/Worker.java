/*
 * Copyright 2020-2024 fluxion Team (https://github.com/fluxion-io).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxion.worker.core;

import io.fluxion.worker.core.task.Task;

/**
 * Worker 行为方法定义
 *
 * @author Brozen
 * @since 2022-09-11
 */
public interface Worker {

    /**
     * 启动当前 Worker
     */
    void start();

    /**
     * 接收 Broker 发送来的任务
     *
     * @param task 任务数据
     */
    void receive(Task task);

    /**
     * 停止当前 Worker
     */
    void stop();

}
