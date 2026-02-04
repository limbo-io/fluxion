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

import io.fluxion.server.infrastructure.schedule.task.DelayedTask;
import lombok.extern.slf4j.Slf4j;

/**
 * 延迟执行一次
 *
 * @author Brozen
 * @since 2022-10-11
 */
@Slf4j
public class DelayedTaskScheduler extends AbstractTaskScheduler<DelayedTask> {

    public DelayedTaskScheduler(Timer timer) {
        super(timer);
    }

    @Override
    protected void run(DelayedTask task) {
        // 直接执行延迟任务
        task.run();
    }

    @Override
    protected void afterExecute(DelayedTask task, Throwable thrown) {
        // 执行后处理，可以在这里添加：
        // - 结果回调通知
        // - 日志记录
        // - 监控上报
        if (thrown != null) {
            log.error("DelayedTask [{}] failed: {}", task.id(), thrown.getMessage());
        } else {
            log.debug("DelayedTask [{}] completed successfully", task.id());
        }
    }

    @Override
    protected boolean shouldCleanup(DelayedTask task, Throwable thrown) {
        // 延迟任务只执行一次，执行后需要清理
        // 即使执行失败也需要清理，以便相同ID的任务可以再次调度
        return true;
    }
}
