/*
 * Copyright 2025-2030 Limbo Team (https://github.com/limbo-io).
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

package io.fluxion.test.core.schedule;

import io.fluxion.server.infrastructure.schedule.scheduler.DelayedTaskScheduler;
import io.fluxion.server.infrastructure.schedule.scheduler.Timer;
import io.fluxion.server.infrastructure.schedule.task.DelayedTask;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * DelayedTaskScheduler 单元测试
 * <p>
 * DelayedTaskScheduler 是 AbstractTaskScheduler 的实现类，用于调度延迟执行一次的任务。
 * 任务特点：
 * - 只在指定的触发时间执行一次
 * - 执行后自动从调度中移除
 * - 相同ID的任务不会重复调度
 *
 * @author Devil
 */
class DelayedTaskSchedulerTest {

    private FakeTimer timer;
    private DelayedTaskScheduler scheduler;

    @BeforeEach
    void setUp() {
        timer = new FakeTimer();
        scheduler = new DelayedTaskScheduler(timer);
    }

    /**
     * 测试延迟任务调度基本功能
     * <p>
     * 验证：
     * 1. 任务被成功注册到 Timer
     * 2. 执行后任务逻辑被正确触发
     */
    @Test
    void testScheduleDelayedTask() {
        AtomicBoolean executed = new AtomicBoolean(false);
        LocalDateTime triggerAt = LocalDateTime.now().plusSeconds(1);
        DelayedTask task = new DelayedTask("test-task", triggerAt, t -> executed.set(true));

        scheduler.schedule(task);

        Assertions.assertTrue(timer.hasScheduledTask());
        timer.executeScheduledTask();
        Assertions.assertTrue(executed.get());
    }

    /**
     * 测试延迟任务执行后从调度中移除
     * <p>
     * DelayedTaskScheduler 的 afterExecute 会调用 stop() 移除任务。
     * 验证：
     * 1. 首次执行后任务被移除
     * 2. 相同ID可以再次调度（因为是单次任务）
     */
    @Test
    void testDelayedTaskExecutionRemovesFromScheduling() {
        AtomicBoolean executed1 = new AtomicBoolean(false);
        AtomicBoolean executed2 = new AtomicBoolean(false);
        LocalDateTime triggerAt = LocalDateTime.now().plusSeconds(1);

        DelayedTask task = new DelayedTask("same-task", triggerAt, t -> executed1.set(true));
        scheduler.schedule(task);

        timer.executeScheduledTask();
        Assertions.assertTrue(executed1.get());

        executed1.set(false);
        DelayedTask task2 = new DelayedTask("same-task", triggerAt, t -> executed2.set(true));
        scheduler.schedule(task2);

        Assertions.assertTrue(timer.hasScheduledTask());
        timer.executeScheduledTask();

        Assertions.assertTrue(executed2.get());
    }

    /**
     * 测试重复任务不会被重复调度
     * <p>
     * AbstractTaskScheduler 使用 ConcurrentHashMap.putIfAbsent 防止重复。
     * 验证：
     * 1. 相同ID的任务只调度一次
     * 2. Timer 只收到一个 schedule 调用
     */
    @Test
    void testDuplicateTaskNotScheduled() {
        AtomicReference<String> firstResult = new AtomicReference<>();
        AtomicReference<String> secondResult = new AtomicReference<>();
        LocalDateTime triggerAt = LocalDateTime.now().plusSeconds(1);

        DelayedTask task1 = new DelayedTask("duplicate-task", triggerAt, t -> firstResult.set("first"));
        DelayedTask task2 = new DelayedTask("duplicate-task", triggerAt, t -> secondResult.set("second"));

        scheduler.schedule(task1);
        scheduler.schedule(task2);

        Assertions.assertEquals(1, timer.getScheduledCount());
    }

    /**
     * 测试停止任务
     * <p>
     * 验证：
     * 1. 调用 stop() 后任务状态变为 stopped
     * 2. 停止后执行 scheduled task 不会触发业务逻辑
     */
    @Test
    void testStopTask() {
        AtomicBoolean executed = new AtomicBoolean(false);
        LocalDateTime triggerAt = LocalDateTime.now().plusSeconds(5);
        DelayedTask task = new DelayedTask("stop-task", triggerAt, t -> executed.set(true));

        scheduler.schedule(task);
        scheduler.stop("stop-task");

        Assertions.assertTrue(task.stopped());
        timer.executeScheduledTask();

        Assertions.assertFalse(executed.get());
    }

    /**
     * 测试触发时间为过去的任务
     * <p>
     * 当 triggerAt 早于当前时间时，延迟应为0，任务立即执行。
     * 验证：
     * 1. past 时间的任务可以正常调度
     * 2. 执行时业务逻辑被触发
     */
    @Test
    void testTaskWithPastTriggerTime() {
        AtomicBoolean executed = new AtomicBoolean(false);
        LocalDateTime pastTriggerAt = LocalDateTime.now().minusSeconds(1);
        DelayedTask task = new DelayedTask("past-task", pastTriggerAt, t -> executed.set(true));

        scheduler.schedule(task);

        timer.executeScheduledTask();
        Assertions.assertTrue(executed.get());
    }

    /**
     * 测试停止不存在的任务
     * <p>
     * 验证：
     * 1. 停止不存在的任务不会抛出异常
     * 2. 系统能优雅处理这种情况
     */
    @Test
    void testStopNonExistentTask() {
        Assertions.assertDoesNotThrow(() -> scheduler.stop("non-existent"));
    }

    /**
     * 测试任务延迟计算
     * <p>
     * 验证 calDelay 方法正确计算延迟时间：
     * 1. 当前时间到 triggerAt 的时间差
     * 2. 延迟值在预期范围内（允许一定误差）
     */
    @Test
    void testTaskDelayCalculation() {
        long delaySeconds = 3;
        LocalDateTime futureTriggerAt = LocalDateTime.now().plusSeconds(delaySeconds);
        DelayedTask task = new DelayedTask("delay-task", futureTriggerAt, t -> {});

        scheduler.schedule(task);

        long actualDelay = timer.getLastDelay();
        long expectedDelayMillis = delaySeconds * 1000;

        Assertions.assertTrue(actualDelay >= 0 && actualDelay <= expectedDelayMillis + 100);
    }

    /**
     * 模拟 Timer 实现用于测试
     * <p>
     * FakeTimer 捕获 schedule 调用的参数，并可以手动触发任务执行。
     * 用于隔离测试，避免依赖真实的定时器实现。
     */
    static class FakeTimer implements Timer {
        private Runnable scheduledRunnable;
        private long lastDelay;
        private int scheduledCount = 0;

        @Override
        public void schedule(Runnable runnable, long delay, TimeUnit unit) {
            this.scheduledRunnable = runnable;
            this.lastDelay = unit.toMillis(delay);
            this.scheduledCount++;
        }

        boolean hasScheduledTask() {
            return scheduledRunnable != null;
        }

        void executeScheduledTask() {
            if (scheduledRunnable != null) {
                scheduledRunnable.run();
            }
        }

        long getLastDelay() {
            return lastDelay;
        }

        int getScheduledCount() {
            return scheduledCount;
        }
    }
}
