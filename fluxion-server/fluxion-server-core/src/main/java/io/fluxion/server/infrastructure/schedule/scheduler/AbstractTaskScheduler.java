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

package io.fluxion.server.infrastructure.schedule.scheduler;

import io.fluxion.server.infrastructure.schedule.task.AbstractTask;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 内存Task的调度器
 * 1. 异常处理，确保失败时清理资源
 * 2. 任务状态跟踪（SCHEDULED/RUNNING/COMPLETED/FAILED/CANCELLED）
 * 3. 使用 ReentrantReadWriteLock 保护状态变更
 * 4. 添加任务统计信息
 * 5. 支持查询任务状态和统计
 *
 * @author Brozen
 * @since 2022-10-11
 */
@Slf4j
public abstract class AbstractTaskScheduler<T extends AbstractTask> implements Scheduler<T> {
    protected final Timer timer;
    protected final Map<String, T> scheduling;
    protected final Map<String, TaskState> taskStates;
    protected final SchedulerStats stats;
    protected final ReentrantReadWriteLock stateLock;
    protected final TimeUnit scheduleUnit = TimeUnit.MILLISECONDS;

    public AbstractTaskScheduler(Timer timer) {
        this.timer = timer;
        this.scheduling = new ConcurrentHashMap<>();
        this.taskStates = new ConcurrentHashMap<>();
        this.stats = new SchedulerStats();
        this.stateLock = new ReentrantReadWriteLock();
    }

    @Override
    public void schedule(T task) {
        String taskId = task.id();

        stateLock.writeLock().lock();
        try {
            // 检查任务是否已在活跃状态
            TaskState currentState = taskStates.get(taskId);
            if (currentState != null && currentState.isActive()) {
                log.warn("Task [{}] is already scheduled with state: {}", taskId, currentState);
                return;
            }

            // 尝试放入调度表
            T existing = scheduling.putIfAbsent(taskId, task);
            if (existing != null && existing != task) {
                log.warn("Task [{}] already exists with different instance", taskId);
                return;
            }

            try {
                updateState(taskId, TaskState.SCHEDULED);
                doSchedule(task);
                stats.recordScheduled();
            } catch (Exception e) {
                log.error("Failed to schedule task [{}], cleaning up", taskId, e);
                cleanup(taskId);
                stats.recordScheduleFailure();
                throw e;
            }
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    protected void doSchedule(T task) {
        String taskId = task.id();
        Long delay = calDelay(task);

        if (delay == null) {
            log.warn("Task [{}] has no trigger time, removing", taskId);
            cleanup(taskId);
            return;
        }

        timer.schedule(() -> {
            // 双重检查
            TaskState state = taskStates.get(taskId);
            if (state == null || !state.shouldExecute()) {
                log.debug("Task [{}] is no longer executable, state: {}", taskId, state);
                cleanup(taskId);
                return;
            }

            if (task.stopped()) {
                cleanup(taskId);
                return;
            }

            // 状态转为 RUNNING
            updateState(taskId, TaskState.RUNNING);
            stats.recordStarted();

            Throwable thrown = null;
            try {
                log.debug("Executing task [{}]", taskId);
                run(task);
                updateState(taskId, TaskState.COMPLETED);
                stats.recordCompleted();
            } catch (Throwable e) {
                thrown = e;
                updateState(taskId, TaskState.FAILED);
                stats.recordFailed();
                log.error("Task [{}] execution failed", taskId, e);
            } finally {
                afterExecute(task, thrown);
                // 子类决定是否清理
                if (shouldCleanup(task, thrown)) {
                    cleanup(taskId);
                }
            }
        }, delay, scheduleUnit);
    }

    @Override
    public void stop(String id) {
        stateLock.writeLock().lock();
        try {
            T task = scheduling.get(id);
            if (task == null) {
                return;
            }

            TaskState state = taskStates.get(id);
            if (state == null || state.isTerminal()) {
                return;
            }

            task.stop();
            updateState(id, TaskState.CANCELLED);
            stats.recordCancelled();
            cleanup(id);

            log.debug("Task [{}] stopped", id);
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    /**
     * Stop all scheduled tasks.
     * This cancels all pending tasks in the scheduler.
     */
    public void stopAll() {
        stateLock.writeLock().lock();
        try {
            // Create a copy of task IDs to avoid concurrent modification
            java.util.List<String> taskIds = new java.util.ArrayList<>(scheduling.keySet());
            int cancelled = 0;
            
            for (String taskId : taskIds) {
                T task = scheduling.get(taskId);
                if (task == null) {
                    continue;
                }
                
                TaskState state = taskStates.get(taskId);
                if (state == null || state.isTerminal()) {
                    continue;
                }
                
                task.stop();
                updateState(taskId, TaskState.CANCELLED);
                stats.recordCancelled();
                cleanup(taskId);
                cancelled++;
            }
            
            if (cancelled > 0) {
                log.info("Stopped {} scheduled tasks", cancelled);
            }
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    /**
     * 计算延迟时间
     */
    protected Long calDelay(T task) {
        LocalDateTime triggerAt = task.triggerAt();
        if (triggerAt == null) {
            return null;
        }
        long delay = Duration.between(LocalDateTime.now(), triggerAt).toMillis();
        return Math.max(delay, 0);
    }

    /**
     * 更新任务状态
     */
    protected void updateState(String taskId, TaskState newState) {
        TaskState oldState = taskStates.put(taskId, newState);
        if (oldState != newState) {
            log.debug("Task [{}] state: {} -> {}", taskId, oldState, newState);
        }
    }

    /**
     * 清理任务资源
     */
    protected void cleanup(String taskId) {
        scheduling.remove(taskId);
        taskStates.remove(taskId);
    }

    /**
     * 子类决定是否在执行后清理
     */
    protected abstract boolean shouldCleanup(T task, Throwable thrown);

    /**
     * 执行任务
     */
    protected abstract void run(T task);

    /**
     * 执行后处理
     */
    protected abstract void afterExecute(T task, Throwable thrown);

    // ==================== 查询方法 ====================

    public TaskState getTaskState(String taskId) {
        return taskStates.get(taskId);
    }

    public SchedulerStats getStats() {
        return stats;
    }

    public int getActiveTaskCount() {
        return (int) taskStates.values().stream()
                .filter(TaskState::isActive)
                .count();
    }

    // ==================== 状态枚举 ====================

    public enum TaskState {
        SCHEDULED,  // 已调度
        RUNNING,    // 执行中
        COMPLETED,  // 已完成
        FAILED,     // 失败
        CANCELLED;  // 已取消

        public boolean isActive() {
            return this == SCHEDULED || this == RUNNING;
        }

        public boolean shouldExecute() {
            return this == SCHEDULED;
        }

        public boolean isTerminal() {
            return this == COMPLETED || this == FAILED || this == CANCELLED;
        }
    }

    // ==================== 统计 ====================

    public static class SchedulerStats {
        private final AtomicLong scheduled = new AtomicLong(0);
        private final AtomicLong started = new AtomicLong(0);
        private final AtomicLong completed = new AtomicLong(0);
        private final AtomicLong failed = new AtomicLong(0);
        private final AtomicLong cancelled = new AtomicLong(0);
        private final AtomicLong scheduleFailures = new AtomicLong(0);

        public void recordScheduled() { scheduled.incrementAndGet(); }
        public void recordStarted() { started.incrementAndGet(); }
        public void recordCompleted() { completed.incrementAndGet(); }
        public void recordFailed() { failed.incrementAndGet(); }
        public void recordCancelled() { cancelled.incrementAndGet(); }
        public void recordScheduleFailure() { scheduleFailures.incrementAndGet(); }

        public long getScheduled() { return scheduled.get(); }
        public long getStarted() { return started.get(); }
        public long getCompleted() { return completed.get(); }
        public long getFailed() { return failed.get(); }
        public long getCancelled() { return cancelled.get(); }
        public long getScheduleFailures() { return scheduleFailures.get(); }

        public double getSuccessRate() {
            long total = completed.get() + failed.get();
            return total == 0 ? 0.0 : (double) completed.get() / total * 100;
        }
    }

}
