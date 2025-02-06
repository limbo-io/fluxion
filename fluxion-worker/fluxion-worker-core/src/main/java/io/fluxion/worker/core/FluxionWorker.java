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
import io.fluxion.remote.core.client.server.ClientServer;
import io.fluxion.worker.core.discovery.ServerDiscovery;
import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.task.Task;
import io.fluxion.worker.core.task.TaskQueue;
import io.fluxion.worker.core.tracker.TrackerBak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Devil
 */
public class FluxionWorker implements Worker {

    private static final Logger log = LoggerFactory.getLogger(FluxionWorker.class);

    /**
     * 任务执行线程池
     */
    private ExecutorService threadPool;

    private WorkerInfo workerInfo;

    /**
     * 服务发现
     */
    private ServerDiscovery discovery;

    /**
     * Client服务
     */
    private ClientServer clientServer;
    /**
     * 并发执行任务数量
     */
    private int concurrency;

    private TaskQueue taskQueue;

    private final AtomicReference<Status> status;

    /**
     * 创建一个 Worker 实例
     *
     * @param clientServer 远程请求服务
     */
    public FluxionWorker(WorkerInfo workerInfo, ClientServer clientServer, ServerDiscovery discovery) {
        Objects.requireNonNull(clientServer, "client server can't be null");

        this.workerInfo = workerInfo;
        this.clientServer = clientServer;
        this.discovery = discovery;

        this.status = new AtomicReference<>(Status.IDLE);
    }

    @Override
    public void receive(Task task) {
        assertWorkerRunning();

        // 找到执行器，校验是否存在
        Executor executor = workerInfo.getExecutor(task.executorName());
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
            Future<?> future = threadPool.submit(() -> {
                try {
                    context.run();
                } catch (Exception e) {
                    log.error("[ExecuteContext] run error", e);
                } finally {
                    taskQueue.delete(task.taskId());
                }
            });
            context.scheduleFuture(future);
        } catch (RejectedExecutionException e) {
            throw new IllegalStateException("Schedule task in worker failed, maybe work thread exhausted");
        }
    }

    @Override
    public void start() {
        if (!status.compareAndSet(Status.IDLE, Status.INITIALIZING)) {
            return;
        }

        // 启动服务注册发现
        this.discovery.start();

        // 初始化工作线程池
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
        this.clientServer.start(); // 目前由于服务在线程中异步处理，如果启动失败，应该终止broker的心跳启动

        // 更新为运行中
        status.compareAndSet(Status.INITIALIZING, Status.RUNNING);
        log.info("worker start!");
    }

    @Override
    public void stop() {
        if (!status.compareAndSet(Status.RUNNING, Status.TERMINATING)) {
            return;
        }
        this.clientServer.stop();
        this.discovery.stop();
        // 修改状态
        status.compareAndSet(Status.TERMINATING, Status.TERMINATED);
    }

    /**
     * 验证 worker 正在运行中
     */
    private void assertWorkerRunning() {
        if (status.get() != Status.RUNNING) {
            throw new IllegalStateException("Worker is not running: " + status);
        }
    }

    public enum Status {

        /**
         * 闲置
         */
        IDLE,
        /**
         * 初始化中
         */
        INITIALIZING,
        /**
         * 运行中
         */
        RUNNING,
        /**
         * 关闭中
         */
        TERMINATING,
        /**
         * 已经关闭
         */
        TERMINATED,
    }

}
