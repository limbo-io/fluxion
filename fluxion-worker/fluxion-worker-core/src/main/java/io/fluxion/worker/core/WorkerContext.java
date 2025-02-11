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
import io.fluxion.worker.core.task.tracker.TaskTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;

/**
 * @author PengQ
 * @since 0.0.1
 */
public class WorkerContext {

    private static final Logger log = LoggerFactory.getLogger(WorkerContext.class);

    private final WorkerStatus status;


    private final WorkerInfo workerInfo;

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

    public WorkerContext(WorkerInfo workerInfo) {
        this.workerInfo = workerInfo;
        this.status = new WorkerStatus();
    }

    public void initialize() {
        this.tasks = new ConcurrentHashMap<>();
        int queueSize = workerInfo.getQueueSize();
        int concurrency = workerInfo.getConcurrency();
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

    public WorkerStatus status() {
        return status;
    }

    public WorkerInfo workerInfo() {
        return workerInfo;
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
}
