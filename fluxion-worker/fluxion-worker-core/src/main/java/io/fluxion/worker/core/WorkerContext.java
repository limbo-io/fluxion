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

import io.fluxion.common.thread.NamedThreadFactory;
import io.fluxion.remote.core.api.Request;
import io.fluxion.remote.core.client.Client;
import io.fluxion.remote.core.client.ClientFactory;
import io.fluxion.remote.core.client.RetryableClient;
import io.fluxion.remote.core.cluster.Node;
import io.fluxion.remote.core.constants.Protocol;
import io.fluxion.remote.core.constants.WorkerStatus;
import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.task.tracker.TaskTracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author PengQ
 * @since 0.0.1
 */
public class WorkerContext {

    private String appId;

    private String appName;

    private Protocol protocol;

    private String address;

    private String host;

    private int port;

    /**
     * 执行器
     */
    private Map<String, Executor> executors;

    /**
     * Worker 标签
     */
    private Map<String, Set<String>> tags;

    /**
     * 可分配任务总数
     */
    private int queueSize;

    private int concurrency;

    private Client client;

    // ========== Runtime ==========
    /**
     * 当前访问节点
     */
    private Node broker;

    private final AtomicReference<WorkerStatus> status;

    /**
     * 当前 Worker 的所有任务存储在此 Map 中
     */
    private Map<String, TaskTracker> tasks;

    /**
     * 任务执行线程池
     */
    private ExecutorService taskProcessExecutor;

    /**
     * 任务状态上报线程池
     */
    private ScheduledExecutorService taskStatusReportExecutor;

    public WorkerContext(String appName, Protocol protocol, String host, int port,
                         int queueSize, int concurrency,
                         List<Executor> executors, Map<String, Set<String>> tags) {
        this.appName = appName;
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.executors = executors == null ? Collections.emptyMap() : executors.stream().collect(Collectors.toMap(Executor::name, executor -> executor));
        this.tags = tags == null ? Collections.emptyMap() : tags;
        this.queueSize = queueSize;
        this.concurrency = concurrency;
        this.client = RetryableClient.builder()
            .client(ClientFactory.create(protocol))
            .build();
        this.status = new AtomicReference<>(WorkerStatus.IDLE);
        this.address = host + ":" + port;
    }

    public void initialize() {
        this.tasks = new ConcurrentHashMap<>();
        // 初始化工作线程池
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(queueSize <= 0 ? concurrency : queueSize);
        this.taskProcessExecutor = new ThreadPoolExecutor(
            concurrency, concurrency,
            5, TimeUnit.SECONDS, queue,
            NamedThreadFactory.newInstance("FluxionTaskProcessExecutor"),
            (r, e) -> {
                throw new RejectedExecutionException();
            }
        );
        // 初始化状态上报线程池
        this.taskStatusReportExecutor = new ScheduledThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 4,
            NamedThreadFactory.newInstance("FluxionTaskStatusReportExecutor")
        );
    }

    public void destroy() {
        taskProcessExecutor.shutdown();
        taskStatusReportExecutor.shutdown();
    }

    /**
     * 状态更新
     *
     * @param expect 预期值（旧值）
     * @param update 更新值（新值）
     * @return 是否成功
     */
    public boolean status(WorkerStatus expect, WorkerStatus update) {
        return status.compareAndSet(expect, update);
    }

    public WorkerStatus status() {
        return status.get();
    }

    public Map<String, TaskTracker> tasks() {
        return tasks;
    }

    public ExecutorService taskProcessExecutor() {
        return taskProcessExecutor;
    }

    public ScheduledExecutorService taskStatusReportExecutor() {
        return taskStatusReportExecutor;
    }

    public String appName() {
        return appName;
    }

    public List<Executor> executors() {
        return new ArrayList<>(executors.values());
    }

    public Executor executor(String name) {
        return executors.get(name);
    }

    public Map<String, Set<String>> tags() {
        return tags;
    }

    public String address() {
        return address;
    }

    public int queueSize() {
        return queueSize;
    }

    public String appId() {
        return appId;
    }

    public void appId(String appId) {
        this.appId = appId;
    }

    public Protocol protocol() {
        return protocol;
    }

    public <R> R call(String path, Request<R> request) {
        return client.call(path, broker.host(), broker.port(), request).getData();
    }

    public void broker(Node broker) {
        this.broker = broker;
    }
}
