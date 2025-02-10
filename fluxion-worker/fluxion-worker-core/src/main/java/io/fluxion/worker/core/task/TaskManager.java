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

package io.fluxion.worker.core.task;

import io.fluxion.common.thread.NamedThreadFactory;
import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.worker.core.WorkerInfo;
import io.fluxion.worker.core.WorkerStatus;
import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.tracker.TrackerBak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * @author PengQ
 * @since 0.0.1
 */
public class TaskManager {

    private static final Logger log = LoggerFactory.getLogger(TaskManager.class);

    private int concurrency;

    private final TaskQueue taskQueue;

    private final WorkerInfo workerInfo;

    /**
     * 任务执行线程池
     */
    private ExecutorService threadPool;

    public TaskManager(int queueSize, int concurrency, WorkerInfo workerInfo) {
        this.concurrency = concurrency;
        this.workerInfo = workerInfo;
        this.taskQueue = new TaskQueue(queueSize);
    }

    public boolean receive(Task task) {
        if (!status().isRunning()) {
            log.info("Worker is not running: {}", status().status());
            return false;
        }

        int availableQueueSize = taskQueue.availableQueueSize();
        if (availableQueueSize <= 0) {
            log.info("Worker's queue is full, limit: {}", availableQueueSize);
            return false;
        }

        Executor executor = workerInfo.getExecutor(task.executorName());

        // 存储任务，并判断是否重复接收任务
        TrackerBak context = new TrackerBak(null, executor, task);
        if (!taskQueue.save(context)) {
            log.info("Receive task [{}], but already in repository", task.taskId());
            return true;
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
            log.error("Schedule task in worker failed, maybe work thread exhausted task:{}", JacksonUtils.toJSONString(task));
            return false;
        }
        return true;
    }

    public void start() {
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
    }

    public void destroy() {
        threadPool.shutdown();
    }

    private WorkerStatus status() {
        return workerInfo.status();
    }
}
