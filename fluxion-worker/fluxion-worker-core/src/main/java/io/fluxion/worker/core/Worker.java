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

import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.task.Task;
import org.apache.commons.collections4.MultiValuedMap;

import java.net.URL;
import java.time.Duration;
import java.util.Map;

/**
 * Worker 行为方法定义
 *
 * @author Brozen
 * @since 2022-09-11
 */
public interface Worker {

    /**
     * 获取 Worker ID
     */
    String getId();

    /**
     * 获取 Worker 名称
     */
    String getName();

    /**
     * 获取 Worker RPC 通信用到的 URL
     */
    URL remoteUrl();

    /**
     * 获取此 Worker 标记的标签，返回的标签不可修改。
     */
    MultiValuedMap<String, String> getTags();

    /**
     * 为此 Worker 添加标签
     *
     * @param key   标签 key
     * @param value 标签 value
     */
    void addTag(String key, String value);

    /**
     * 添加任务执行器
     */
    void addExecutor(Executor executor);

    /**
     * 获取当前 Worker 中的执行器
     */
    Map<String, Executor> executors();

    /**
     * 启动当前 Worker
     *
     * @param heartbeatPeriod 心跳间隔
     */
    void start(Duration heartbeatPeriod);

    /**
     * Just beat it
     * 发送心跳
     */
    void heartbeat();

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
