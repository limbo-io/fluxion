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

import io.fluxion.server.infrastructure.schedule.Calculable;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import io.fluxion.server.infrastructure.schedule.task.PeriodicTask;
import io.limbo.utils.time.TimeUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

/**
 * 周期task调度器
 *
 * @author Brozen
 * @since 2022-10-11
 */
@Slf4j
public class PeriodicTaskScheduler extends AbstractTaskScheduler<PeriodicTask> {

    public PeriodicTaskScheduler(Timer timer) {
        super(timer);
    }

    @Override
    protected void run(PeriodicTask task) {
        String taskId = task.id();

        // 获取调度选项
        Calculable calculation = task.calculation();
        if (calculation == null) {
            log.warn("Task [{}] has no calculation, skipping", taskId);
            task.stop();
            return;
        }

        ScheduleOption option = calculation.scheduleOption();
        if (option == null || option.getType() == null || option.getType() == ScheduleType.UNKNOWN) {
            log.error("Task [{}] has invalid schedule option: {}", taskId, option);
            task.stop();
            return;
        }

        // 检查时间窗口
        if (!isInTimeWindow(option)) {
            log.info("Task [{}] is outside time window, stopping", taskId);
            task.stop();
            return;
        }

        ScheduleType scheduleType = option.getType();
        log.debug("Task [{}] executing with schedule type: {}", taskId, scheduleType);

        // 根据调度类型执行
        switch (scheduleType) {
            case FIXED_RATE:
            case CRON:
                // FIXED_RATE 和 CRON: 先重新调度，再执行
                reschedule(task);
                executeTask(task);
                break;

            case FIXED_DELAY:
                // FIXED_DELAY: 先执行，再重新调度
                executeTask(task);
                reschedule(task);
                break;

            default:
                log.error("Unknown schedule type: {} for task [{}]", scheduleType, taskId);
                task.stop();
        }
    }

    @Override
    protected void afterExecute(PeriodicTask task, Throwable thrown) {
        String taskId = task.id();

        if (thrown != null) {
            log.error("ScheduledTask [{}] failed: {}", taskId, thrown.getMessage());
        } else {
            log.debug("ScheduledTask [{}] completed successfully", taskId);
        }

        if (task.stopped()) {
            log.info("Task [{}] is stopped, will not reschedule", taskId);
            return;
        }

        // The next trigger has already been scheduled by run().  Restore the
        // executable state after AbstractTaskScheduler marks this invocation
        // completed, otherwise the next timer callback is rejected.
        updateState(taskId, TaskState.SCHEDULED);
    }

    @Override
    protected boolean shouldCleanup(PeriodicTask task, Throwable thrown) {
        // 只有当任务被停止时才清理
        // 循环任务保持 scheduling 中的引用，除非显式 stop
        return task.stopped();
    }

    /**
     * 检查当前时间是否在调度窗口内
     */
    private boolean isInTimeWindow(ScheduleOption option) {
        LocalDateTime now = TimeUtils.currentLocalDateTime();

        LocalDateTime startTime = option.getStartTime();
        LocalDateTime endTime = option.getEndTime();

        // 检查开始时间
        if (startTime != null && startTime.isAfter(now)) {
            log.debug("Current time {} is before start time {}", now, startTime);
            return false;
        }

        // 检查结束时间
        if (endTime != null && endTime.isBefore(now)) {
            log.debug("Current time {} is after end time {}", now, endTime);
            return false;
        }

        return true;
    }

    /**
     * 执行实际任务
     */
    private void executeTask(PeriodicTask task) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("Task [{}] execution failed", task.id(), e);
            throw e;
        }
    }

    /**
     * 重新调度任务
     */
    private void reschedule(PeriodicTask task) {
        String taskId = task.id();

        try {
            // 计算下次触发时间
            PeriodicTask nextTask = task.nextTrigger();
            LocalDateTime nextTriggerAt = nextTask.triggerAt();

            if (nextTriggerAt == null) {
                log.info("Task [{}] has no more trigger times, stopping", taskId);
                task.stop();
                return;
            }

            log.debug("Task [{}] rescheduled for next trigger at: {}", taskId, nextTriggerAt);

            // 重新调度
            // 注意：这里不更新状态，让新的调度流程处理
            doSchedule(nextTask);

        } catch (Exception e) {
            log.error("Task [{}] reschedule failed", taskId, e);
        }
    }

}
