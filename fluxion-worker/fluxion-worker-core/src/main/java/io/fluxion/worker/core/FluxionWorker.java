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

package io.fluxion.worker.core;

import io.fluxion.remote.core.api.constants.WorkerStatus;
import io.fluxion.remote.core.client.server.ClientServer;
import io.fluxion.worker.core.discovery.ServerDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * @author Devil
 */
public class FluxionWorker implements Worker {

    private static final Logger log = LoggerFactory.getLogger(FluxionWorker.class);

    private final WorkerContext workerContext;

    /**
     * 服务发现
     */
    private final ServerDiscovery discovery;

    /**
     * Client服务
     */
    private final ClientServer clientServer;


    /**
     * 创建一个 Worker 实例
     */
    public FluxionWorker(WorkerContext workerContext, ClientServer clientServer, ServerDiscovery discovery) {
        Objects.requireNonNull(workerContext, "WorkerContext can't be null");
        Objects.requireNonNull(clientServer, "ClientServer can't be null");
        Objects.requireNonNull(discovery, "ServerDiscovery can't be null");

        this.workerContext = workerContext;
        this.clientServer = clientServer;
        this.discovery = discovery;
    }

    @Override
    public void start() {
        if (!workerContext.changeStatus(WorkerStatus.IDLE, WorkerStatus.INITIALIZING)) {
            return;
        }
        // Launch the program in order

        // 初始化工作线程池等
        this.workerContext.initialize();

        // 启动RPC服务
        this.clientServer.start(); // 目前由于服务在线程中异步处理，如果启动失败，应该终止broker的心跳启动

        // 启动服务注册发现
        this.discovery.start();

        // 更新为运行中
        workerContext.changeStatus(WorkerStatus.INITIALIZING, WorkerStatus.RUNNING);
        log.info("FluxionWorker Start!!!");
    }

    @Override
    public void stop() {
        if (!workerContext.changeStatus(WorkerStatus.RUNNING, WorkerStatus.TERMINATING)) {
            return;
        }
        this.discovery.stop();
        this.clientServer.stop();
        this.workerContext.destroy();
        // 修改状态
        workerContext.changeStatus(WorkerStatus.TERMINATING, WorkerStatus.TERMINATED);
    }

}
