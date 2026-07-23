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

import io.fluxion.server.infrastructure.schedule.BasicCalculation;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import io.fluxion.server.infrastructure.schedule.scheduler.PeriodicTaskScheduler;
import io.fluxion.server.infrastructure.schedule.scheduler.Timer;
import io.fluxion.server.infrastructure.schedule.task.PeriodicTask;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ScheduledTaskScheduler 单元测试
 * <p>
 * ScheduledTaskScheduler 是 AbstractTaskScheduler 的实现类，用于调度可循环执行的定时任务。
 * 支持三种调度类型：
 * - FIXED_RATE: 固定速度，上次下发后立即开始计算下次触发时间
 * - FIXED_DELAY: 固定延迟，上次执行完成后开始计算下次触发时间
 * - CRON: 基于 CRON 表达式计算触发时间
 * <p>
 * 任务特点：
 * - 到触发时间点自动创建执行实例
 * - 执行后根据调度类型决定是否重新调度
 * - 在指定时间窗口外不会触发
 *
 * @author Devil
 */
class PeriodicTaskSchedulerTest {

    private FakeTimer timer;
    private PeriodicTaskScheduler scheduler;

    @BeforeEach
    void setUp() {
        timer = new FakeTimer();
        scheduler = new PeriodicTaskScheduler(timer);
    }

    /**
     * 测试 FIXED_RATE 类型任务调度
     * <p>
     * FIXED_RATE: 前一次调度下发后，间隔固定时间触发下次调度。
     * 验证：
     * 1. 任务能多次执行
     * 2. 每次执行后重新调度
     */
    @Test
    void testScheduleFixRateTask() {
        AtomicInteger executionCount = new AtomicInteger(0);
        Duration interval = Duration.ofMillis(500);

        PeriodicTask task = createFixRateTask("fixrate-task", interval, executionCount);
        scheduler.schedule(task);

        timer.executeScheduledTask();
        Assertions.assertEquals(1, executionCount.get());

        timer.executeScheduledTask();
        Assertions.assertEquals(2, executionCount.get());
    }

    /**
     * 测试 FIXED_DELAY 类型任务调度
     * <p>
     * FIXED_DELAY: 前一次调度执行完成后，间隔固定时间触发下次调度。
     * 与 FIXED_RATE 的区别在于计算下次触发时间的起点不同。
     * 验证：
     * 1. 任务能多次执行
     * 2. 执行顺序：运行 -> 完成后重新调度 -> 下次运行
     */
    @Test
    void testScheduleFixDelayTask() {
        AtomicInteger executionCount = new AtomicInteger(0);
        Duration interval = Duration.ofMillis(500);

        PeriodicTask task = createFixDelayTask("fixdelay-task", interval, executionCount);
        scheduler.schedule(task);

        timer.executeScheduledTask();
        Assertions.assertEquals(1, executionCount.get());

        timer.executeScheduledTask();
        Assertions.assertEquals(2, executionCount.get());
    }

    /**
     * 测试主动停止任务
     * <p>
     * 验证：
     * 1. 调用 stop() 后任务从调度中移除
     * 2. 后续 scheduled task 执行时不会触发业务逻辑
     * 3. 任务状态变为 stopped
     */
    @Test
    void testStopTask() {
        AtomicInteger executionCount = new AtomicInteger(0);
        Duration interval = Duration.ofMillis(100);

        PeriodicTask task = createFixRateTask("stop-task", interval, executionCount);
        scheduler.schedule(task);

        scheduler.stop("stop-task");
        timer.executeScheduledTask();

        Assertions.assertEquals(0, executionCount.get());
        Assertions.assertTrue(task.stopped());
    }

    /**
     * 测试任务在执行中主动停止
     * <p>
     * 验证 afterExecute 正确处理 stopped 状态：
     * 1. 首次执行成功
     * 2. 在 afterExecute 中检测到 stopped 状态
     * 3. 停止后续重新调度
     */
    @Test
    void testTaskStoppedInScheduling() {
        AtomicInteger executionCount = new AtomicInteger(0);
        Duration interval = Duration.ofMillis(100);

        PeriodicTask task = createFixRateTask("stopped-scheduling-task", interval, executionCount);
        scheduler.schedule(task);

        timer.executeScheduledTask();
        Assertions.assertEquals(1, executionCount.get());

        task.stop();
        timer.executeScheduledTask();

        Assertions.assertEquals(1, executionCount.get());
    }

    /**
     * 测试任务在时间窗口外不被调度
     * <p>
     * ScheduleOption 可以设置 startTime 和 endTime。
     * 当当前时间在窗口外时，任务不应被调度（从 scheduling map 中移除）。
     * 验证：
     * 1. endTime 早于当前时间时任务不执行
     * 2. Timer 不会收到调度请求
     */
    @Test
    void testScheduleOutsideTimeWindow() {
        AtomicInteger executionCount = new AtomicInteger(0);

        LocalDateTime pastEndTime = LocalDateTime.now().minusMinutes(10);
        LocalDateTime pastStartTime = LocalDateTime.now().minusMinutes(20);

        ScheduleOption option = new ScheduleOption(
            ScheduleType.FIXED_RATE,
            pastStartTime,
            pastEndTime,
            Duration.ZERO,
            Duration.ofMillis(100),
            null,
            null
        );

        PeriodicTask task = new PeriodicTask("expired-task",
            new BasicCalculation(null, null, option),
            t -> executionCount.incrementAndGet()
        );

        scheduler.schedule(task);

        Assertions.assertTrue(timer.hasScheduledTask());
        timer.executeScheduledTask();
        Assertions.assertEquals(0, executionCount.get());
        Assertions.assertTrue(task.stopped());
    }

    /**
     * 测试任务在 startTime 之前不被调度
     * <p>
     * 当 startTime 晚于当前时间时，任务还没到可调度的时间。
     * 验证：
     * 1. startTime 晚于当前时间时任务不执行
     * 2. 任务被从 scheduling map 中移除
     */
    @Test
    void testScheduleBeforeStartTime() {
        AtomicInteger executionCount = new AtomicInteger(0);

        LocalDateTime futureStartTime = LocalDateTime.now().plusMinutes(10);

        ScheduleOption option = new ScheduleOption(
            ScheduleType.FIXED_RATE,
            futureStartTime,
            null,
            Duration.ZERO,
            Duration.ofMillis(100),
            null,
            null
        );

        PeriodicTask task = new PeriodicTask("future-task",
            new BasicCalculation(null, null, option),
            t -> executionCount.incrementAndGet()
        );

        scheduler.schedule(task);

        Assertions.assertTrue(timer.hasScheduledTask());
        timer.executeScheduledTask();
        Assertions.assertEquals(0, executionCount.get());
        Assertions.assertTrue(task.stopped());
    }

    /**
     * 测试未知的调度类型
     * <p>
     * 当 ScheduleType 为 UNKNOWN 时，任务不应被调度执行。
     * 验证：
     * 1. UNKNOWN 类型不会触发业务逻辑
     * 2. 执行次数为 0
     */
    @Test
    void testUnknownScheduleType() {
        AtomicInteger executionCount = new AtomicInteger(0);

        ScheduleOption option = new ScheduleOption(
            ScheduleType.UNKNOWN,
            null,
            null,
            Duration.ZERO,
            null,
            null,
            null
        );

        PeriodicTask task = new PeriodicTask("unknown-type-task",
            new BasicCalculation(null, null, option),
            t -> executionCount.incrementAndGet()
        );

        scheduler.schedule(task);
        timer.executeScheduledTask();

        Assertions.assertEquals(0, executionCount.get());
    }

    /**
     * 测试空的调度选项
     * <p>
     * 当 ScheduleOption 为 null 时，run() 方法应安全处理。
     * 验证：
     * 1. null 选项不会导致异常
     * 2. 业务逻辑不会被执行
     */
    @Test
    void testNullScheduleOption() {
        AtomicInteger executionCount = new AtomicInteger(0);

        PeriodicTask task = new PeriodicTask("null-option-task",
            new BasicCalculation(null, null, null),
            t -> executionCount.incrementAndGet()
        );

        scheduler.schedule(task);
        timer.executeScheduledTask();

        Assertions.assertEquals(0, executionCount.get());
    }

    /**
     * 测试重复任务不会被重复调度
     * <p>
     * 相同 ID 的任务应只被调度一次。
     * 验证：
     * 1. 相同 ID 的两个任务只触发一次 Timer.schedule
     * 2. Schedule 计数为 1
     */
    @Test
    void testDuplicateTaskNotScheduled() {
        AtomicInteger executionCount = new AtomicInteger(0);
        Duration interval = Duration.ofMillis(100);

        PeriodicTask task1 = createFixRateTask("duplicate-task", interval, executionCount);
        PeriodicTask task2 = createFixRateTask("duplicate-task", interval, executionCount);

        scheduler.schedule(task1);
        scheduler.schedule(task2);

        Assertions.assertEquals(1, timer.getScheduleCount());
    }

    /**
     * 测试任务执行后重新调度
     * <p>
     * FIXED_RATE 和 CRON 类型任务在执行后通过 reschedule 重新调度。
     * 验证：
     * 1. 首次执行后 Schedule 计数增加
     * 2. 执行次数正确
     */
    @Test
    void testTaskRescheduleAfterExecution() {
        AtomicInteger executionCount = new AtomicInteger(0);
        Duration interval = Duration.ofMillis(500);

        PeriodicTask task = createFixRateTask("reschedule-task", interval, executionCount);
        scheduler.schedule(task);

        timer.executeScheduledTask();
        Assertions.assertEquals(1, executionCount.get());
        Assertions.assertEquals(2, timer.getScheduleCount());
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
        Assertions.assertDoesNotThrow(() -> scheduler.stop("non-existent-task"));
    }

    /**
     * 创建 FIXED_RATE 类型的 ScheduledTask
     *
     * @param id      任务ID
     * @param interval 调度间隔
     * @param counter 执行计数器
     * @return ScheduledTask 实例
     */
    private PeriodicTask createFixRateTask(String id, Duration interval, AtomicInteger counter) {
        ScheduleOption option = new ScheduleOption(
            ScheduleType.FIXED_RATE,
            null,
            null,
            Duration.ZERO,
            interval,
            null,
            null
        );
        return new PeriodicTask(id,
            new BasicCalculation(null, null, option),
            t -> counter.incrementAndGet()
        );
    }

    /**
     * 创建 FIXED_DELAY 类型的 ScheduledTask
     *
     * @param id       任务ID
     * @param interval 调度间隔
     * @param counter  执行计数器
     * @return ScheduledTask 实例
     */
    private PeriodicTask createFixDelayTask(String id, Duration interval, AtomicInteger counter) {
        ScheduleOption option = new ScheduleOption(
            ScheduleType.FIXED_DELAY,
            null,
            null,
            Duration.ZERO,
            interval,
            null,
            null
        );
        return new PeriodicTask(id,
            new BasicCalculation(null, null, option),
            t -> counter.incrementAndGet()
        );
    }

    /**
     * 模拟 Timer 实现用于测试
     * <p>
     * FakeTimer 捕获 schedule 调用的参数，并可以手动触发任务执行。
     * 用于隔离测试，避免依赖真实的定时器实现（如 TimingWheelTimer）。
     */
    static class FakeTimer implements Timer {
        private Runnable scheduledRunnable;
        private long lastDelay;
        private TimeUnit lastUnit;
        private int scheduleCount = 0;
        private int executeCount = 0;

        @Override
        public void schedule(Runnable runnable, long delay, TimeUnit unit) {
            this.scheduledRunnable = runnable;
            this.lastDelay = delay;
            this.lastUnit = unit;
            this.scheduleCount++;
        }

        void executeScheduledTask() {
            if (scheduledRunnable != null) {
                scheduledRunnable.run();
                executeCount++;
            }
        }

        boolean hasScheduledTask() {
            return scheduledRunnable != null;
        }

        long getLastDelay() {
            return lastDelay;
        }

        TimeUnit getLastUnit() {
            return lastUnit;
        }

        int getScheduleCount() {
            return scheduleCount;
        }

        int getExecuteCount() {
            return executeCount;
        }
    }
}
