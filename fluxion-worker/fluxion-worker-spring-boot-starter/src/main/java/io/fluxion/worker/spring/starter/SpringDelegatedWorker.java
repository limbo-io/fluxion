/*
 * Copyright 2020-2024 Limbo Team (https://github.com/limbo-world).
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

package io.fluxion.worker.spring.starter;

import io.fluxion.remote.core.client.AbstractLBClient;
import io.fluxion.remote.core.client.server.AbstractClientServer;
import io.fluxion.remote.core.client.server.ClientServerConfig;
import io.fluxion.remote.core.client.server.ClientServerFactory;
import io.fluxion.remote.core.client.server.IHandleProcessor;
import io.fluxion.worker.core.FluxionWorker;
import io.fluxion.worker.core.Worker;
import io.fluxion.worker.core.WorkerInfo;
import io.fluxion.worker.core.WorkerStatus;
import io.fluxion.worker.core.discovery.DefaultServerDiscovery;
import io.fluxion.worker.core.discovery.ServerDiscovery;
import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.processor.WorkerRpcHandleProcessor;
import io.fluxion.worker.core.task.TaskManager;
import io.fluxion.worker.spring.starter.processor.event.ExecutorScannedEvent;
import io.fluxion.worker.spring.starter.processor.event.WorkerReadyEvent;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.event.EventListener;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Brozen
 * @since 2022-09-11
 */
public class SpringDelegatedWorker implements Worker, DisposableBean {

    private Worker delegated;
    private List<Executor> executors;

    private final String appName;
    private final URL url;
    private final int queueSize;
    private final int concurrency;
    private final AbstractLBClient lbClient;
    private final Map<String, Set<String>> tags;

    public SpringDelegatedWorker(String appName, URL url, int queueSize, int concurrency, AbstractLBClient lbClient, Map<String, Set<String>> tags) {
        this.appName = appName;
        this.url = url;
        this.queueSize = queueSize;
        this.concurrency = concurrency;
        this.lbClient = lbClient;
        this.tags = tags;
    }


    /**
     * 监听到 ExecutorScannedEvent 事件后，将 TaskExecutor 添加到 Worker
     */
    @EventListener(ExecutorScannedEvent.class)
    public void onExecutorScanned(ExecutorScannedEvent event) {
        this.executors = event.getExecutors();
    }


    /**
     * 监听到 WorkerReadyEvent 事件后，注册并启动当前 Worker
     */
    @EventListener(WorkerReadyEvent.class)
    public void onWorkerReady(WorkerReadyEvent event) {
        // WorkerInfo
        WorkerInfo workerInfo = new WorkerInfo(appName, new WorkerStatus(), url, executors, tags);
        // Discovery
        ServerDiscovery discovery = new DefaultServerDiscovery(workerInfo, lbClient);
        // TaskManager
        TaskManager taskManager = new TaskManager(queueSize, concurrency, workerInfo);
        // ClientServer
        ClientServerFactory factory = ClientServerFactory.instance();
        IHandleProcessor handleProcessor = new WorkerRpcHandleProcessor(taskManager);
        ClientServerConfig clientServerConfig = new ClientServerConfig(url.getPort(), handleProcessor);
        AbstractClientServer clientServer = factory.create(clientServerConfig);
        // Worker
        Worker worker = new FluxionWorker(workerInfo, clientServer, discovery, taskManager);
        this.delegated = worker;
        // Start
        worker.start();
    }

    /**
     * Bean 销毁时，停止 Worker
     */
    @Override
    public void destroy() {
        delegated.stop();
    }

    @Override
    public void start() {
        delegated.start();
    }

    @Override
    public void stop() {
        delegated.stop();
    }

}
