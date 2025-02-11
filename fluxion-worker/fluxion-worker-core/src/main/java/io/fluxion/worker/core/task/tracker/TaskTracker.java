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

package io.fluxion.worker.core.task.tracker;

import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.worker.core.WorkerContext;
import io.fluxion.worker.core.WorkerInfo;
import io.fluxion.worker.core.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Task执行管理和监控
 *
 * @author Devil
 */
public abstract class TaskTracker {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * 从 Broker 接收到的任务
     */
    protected final Task task;

    protected final WorkerContext workerContext;

    protected final AtomicBoolean destroyed;

    protected Future<?> processFuture;

    protected Future<?> statusReportFuture;

    public TaskTracker(Task task, WorkerContext workerContext) {
        this.task = task;
        this.workerContext = workerContext;
        this.destroyed = new AtomicBoolean(false);
    }

    public Task task() {
        return task;
    }

    public boolean start() {
        if (!workerContext.status().isRunning()) {
            log.info("Worker is not running: {}", workerContext.status().status());
            return false;
        }

        if (!save(this)) {
            log.info("Receive task [{}], but already in repository", task.getTaskId());
            return false;
        }

        try {
            // 提交执行 正常来说保存成功这里不会被拒绝
            this.processFuture = workerContext.taskProcessExecutor().submit(() -> {
                try {
                    run();
                } catch (Exception e) {
                    log.error("[ExecuteContext] run error", e);
                } finally {
                    destroy();
                }
            });
            // 提交状态监控
            this.statusReportFuture = workerContext.taskStatusReportExecutor().submit(() -> {
                // todo @d
            });

            return true;
        } catch (RejectedExecutionException e) {
            log.error("Schedule task in worker failed, maybe work thread exhausted task:{}", JacksonUtils.toJSONString(task), e);
            destroy();
            return false;
        } catch (Exception e) {
            log.error("Schedule task in worker failed, task:{}", JacksonUtils.toJSONString(task), e);
            destroy();
            return false;
        }
    }

    /**
     * 尝试新增任务到仓库中：如果已存在相同 taskId 的任务，则不添加新的任务，返回 false；如不存在，则添加成功，返回 true。
     *
     * @param tracker 任务执行上下文
     */
    public boolean save(TaskTracker tracker) {
        WorkerInfo workerInfo = workerContext.workerInfo();
        Map<String, TaskTracker> tasks = workerContext.tasks();
        // 剩余可分配任务数
        int availableQueueSize = workerInfo.getQueueSize() - tasks.size();
        if (availableQueueSize <= 0) {
            log.info("Worker's queue is full, limit: {}", availableQueueSize);
            return false;
        }
        return tasks.putIfAbsent(tracker.task().getTaskId(), tracker) == null;
    }

    public abstract void run();

    public void destroy() {
        if (!destroyed.compareAndSet(false, true)) {
            log.info("TaskTracker taskId: {} has been destroyed", task.getTaskId());
            return;
        }
        if (statusReportFuture != null) {
            statusReportFuture.cancel(true);
        }
        if (processFuture != null) {
            processFuture.cancel(true);
        }
        workerContext.tasks().remove(task.getTaskId());
        log.info("TaskTracker taskId: {} destroyed success", task.getTaskId());
    }

}
