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

package io.fluxion.worker.core.task.tracker;

import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.remote.core.api.request.broker.TaskReportRequest;
import io.fluxion.worker.core.WorkerContext;
import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.task.Task;

import static io.fluxion.remote.core.constants.BrokerRemoteConstant.API_TASK_REPORT;

/**
 * 执行一个任务
 *
 * @author Devil
 */
public class BasicTaskTracker extends TaskTracker {

    private final Executor executor;

    public BasicTaskTracker(Task task, Executor executor, WorkerContext workerContext) {
        super(task, workerContext);
        this.executor = executor;
    }

    @Override
    public void run() {
        // 提交执行 正常来说保存成功这里不会被拒绝
        this.processFuture = workerContext.taskProcessExecutor().submit(() -> {
            try {
                // 反馈执行中 -- 排除由于网络问题导致的失败可能性
                boolean success = reportStart(task);
                if (!success) {
                    // 不成功，可能已经下发给其它节点
                    return;
                }
                executor.run(task);
                // 执行成功
                reportSuccess(task);
            } catch (Throwable throwable) {
                log.error("[BasicTaskTracker] run error", throwable);
                reportFail(task, throwable);
            } finally {
                destroy();
            }
        });
        // 提交状态监控
        this.statusReportFuture = workerContext.taskStatusReportExecutor().submit(() -> {
            TaskReportRequest request = new TaskReportRequest();
            request.setTaskId(task.getTaskId());
            request.setReportAt(TimeUtils.currentLocalDateTime());
            request.setWorkerAddress(workerContext.address());
            workerContext.call(API_TASK_REPORT, request);
        });
    }
}
