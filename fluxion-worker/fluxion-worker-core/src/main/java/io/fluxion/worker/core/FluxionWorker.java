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

package io.fluxion.worker.core;

import io.fluxion.common.thread.NamedThreadFactory;
import io.fluxion.common.utils.SHAUtils;
import io.fluxion.remote.core.constants.WorkerConstant;
import io.fluxion.remote.core.exception.RegisterFailException;
import io.fluxion.remote.core.exception.RpcException;
import io.fluxion.remote.core.server.EmbedRpcServer;
import io.fluxion.remote.core.server.RpcServerStatus;
import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.rpc.BrokerRpc;
import io.fluxion.worker.core.task.Task;
import io.fluxion.worker.core.task.TaskQueue;
import io.fluxion.worker.core.tracker.TrackerBak;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * @author Devil
 */
public class FluxionWorker implements Worker {

    private static final Logger log = LoggerFactory.getLogger(FluxionWorker.class);

    private String id;

    private final String name;

    /**
     * Worker 通信基础 URL
     */
    private URL url;

    /**
     * 任务执行线程池
     */
    private ExecutorService threadPool;

    /**
     * 执行器名称 - 执行器 映射关系
     */
    private final Map<String, Executor> executors;

    /**
     * 远程调用
     */
    private BrokerRpc brokerRpc;

    /**
     * 心跳起搏器
     */
    private WorkerHeartbeat pacemaker;

    /**
     * RPC服务
     */
    private EmbedRpcServer embedRpcServer;
    /**
     * 并发执行任务数量
     */
    private int concurrency;

    private TaskQueue taskQueue;

    /**
     * 创建一个 Worker 实例
     *
     * @param name           worker 实例 名称，如未指定则会随机生成一个
     * @param url            worker 启动的 RPC 服务的 baseUrl
     * @param brokerRpc      broker RPC 通信模块
     * @param embedRpcServer 内置服务
     */
    public FluxionWorker(String name, URL url, BrokerRpc brokerRpc, EmbedRpcServer embedRpcServer) {
        Objects.requireNonNull(url, "URL can't be null");
        Objects.requireNonNull(brokerRpc, "broker client can't be null");

        this.name = StringUtils.isBlank(name) ? SHAUtils.sha1AndHex(url.toString()).toUpperCase() : name;
        this.url = url;
        this.brokerRpc = brokerRpc;
        this.embedRpcServer = embedRpcServer;

        this.executors = new ConcurrentHashMap<>();
    }


    @Override
    public String getId() {
        return "";
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public URL remoteUrl() {
        return null;
    }

    @Override
    public MultiValuedMap<String, String> getTags() {
        return null;
    }

    @Override
    public void addTag(String key, String value) {

    }

    @Override
    public void addExecutor(Executor executor) {

    }

    @Override
    public Map<String, Executor> executors() {
        return Collections.emptyMap();
    }

    @Override
    public void start(Duration heartbeatPeriod) {
        Objects.requireNonNull(heartbeatPeriod);

        // 重复检测
        if (!embedRpcServer.initialize()) {
            return;
        }

        Worker worker = this;

        // 注册
        try {
            registerSelfToBroker();
        } catch (Exception e) {
            log.error("Register to broker has error", e);
            throw new RuntimeException(e);
        }

        // 初始化线程池
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(taskQueue.queueSize() <= 0 ? concurrency : taskQueue.queueSize());
        threadPool = new ThreadPoolExecutor(
            concurrency, concurrency,
            5, TimeUnit.SECONDS, queue,
            NamedThreadFactory.newInstance("FluxionWorkerExecutor"),
            (r, e) -> {
                throw new RejectedExecutionException();
            }
        );

        // 启动RPC服务
        this.embedRpcServer.start(); // 目前由于服务在线程中异步处理，如果启动失败，应该终止broker的心跳启动

        // 心跳
        if (pacemaker == null) {
            pacemaker = new WorkerHeartbeat(worker, Duration.ofSeconds(WorkerConstant.HEARTBEAT_TIMEOUT_SECOND));
        }
        pacemaker.start();

        log.info("worker start!");
    }

    @Override
    public void heartbeat() {
        assertWorkerRunning();

        try {
            brokerRpc.heartbeat();
        } catch (RpcException e) {
            log.warn("Worker send heartbeat failed");
            throw new IllegalStateException("Worker send heartbeat failed", e);
        }
    }

    @Override
    public void receive(Task task) {
        assertWorkerRunning();

        // 找到执行器，校验是否存在
        Executor executor = executors.get(task.executorName());
        Objects.requireNonNull(executor, "Unsupported executor: " + task.executorName());

        int availableQueueSize = taskQueue.availableQueueSize();
        if (availableQueueSize <= 0) {
            throw new IllegalArgumentException("Worker's queue is full, limit: " + availableQueueSize);
        }

        // 存储任务，并判断是否重复接收任务
        TrackerBak context = new TrackerBak(null, executor, task);
        if (!taskQueue.save(context)) {
            log.warn("Receive task [{}], but already in repository", task.taskId());
            return;
        }

        try {
            // 提交执行
            Future<?> future = threadPool.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        context.run();
                    } catch (Exception e) {
                        log.error("[ExecuteContext] run error", e);
                    } finally {
                        taskQueue.delete(task.taskId());
                    }
                }
            });
            context.scheduleFuture(future);
        } catch (RejectedExecutionException e) {
            throw new IllegalStateException("Schedule task in worker failed, maybe work thread exhausted");
        }
    }

    @Override
    public void stop() {
        embedRpcServer.stop();
    }

    /**
     * 验证 worker 正在运行中
     */
    private void assertWorkerRunning() {
        RpcServerStatus status = embedRpcServer.status();
        if (status != RpcServerStatus.RUNNING) {
            throw new IllegalStateException("Worker is not running: " + status);
        }
    }

    /**
     * 向 Broker 注册当前 Worker
     */
    private void registerSelfToBroker() {
        try {
            // 调用 Broker 远程接口，并更新 Broker 信息
            this.id = brokerRpc.register();
        } catch (RegisterFailException e) {
            log.error("Worker register failed", e);
            throw e;
        }

        log.info("register success!");
    }

}
