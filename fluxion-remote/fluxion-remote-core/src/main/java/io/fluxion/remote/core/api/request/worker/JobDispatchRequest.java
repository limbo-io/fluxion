/*
 * Copyright 2025-2030 limbo-io Team (https://github.com/limbo-io).
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

package io.fluxion.remote.core.api.request.worker;

import io.fluxion.remote.core.api.Request;
import io.fluxion.remote.core.constants.ExecuteMode;
import io.fluxion.remote.core.constants.JobDispatchResult;

/**
 * dispatch to worker
 *
 * @author Devil
 * @since 2023/8/3
 */
public class JobDispatchRequest implements Request<JobDispatchResult> {

    private String jobId;
    /**
     * 执行方式
     *
     * @see ExecuteMode
     */
    private String executeMode;

    /**
     * 执行器的名称
     */
    private String executorName;

    /**
     * 下发尝试次数，用于幂等控制
     */
    private int dispatchAttempt;

    /**
     * 激活截止时间，UTC epoch millis；为空表示不设截止，允许迟到执行。
     * 仅约束本次下发对应的首批 Job 启动（见 CONTEXT.md「Execution 激活截止时间」）。
     */
    private Long deadlineEpochMillis;

    /**
     * Job 的原始计划触发时刻（领域名 triggerAt）：UTC epoch millis 编码的绝对时间，为空表示
     * 非调度来源。调度 Execution 的首批 Job 继承 Execution.triggerAt，后续 Workflow 节点是自身
     * 就绪时刻（见 CONTEXT.md「Job 时间语义」）；接收时刻由 Worker 本机自行取得，不由 Broker 提供。
     */
    private Long triggerAt;

    public Long getTriggerAt() {
        return triggerAt;
    }

    public void setTriggerAt(Long triggerAt) {
        this.triggerAt = triggerAt;
    }

    public String getExecuteMode() {
        return executeMode;
    }

    public void setExecuteMode(String executeMode) {
        this.executeMode = executeMode;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getExecutorName() {
        return executorName;
    }

    public void setExecutorName(String executorName) {
        this.executorName = executorName;
    }

    public int getDispatchAttempt() {
        return dispatchAttempt;
    }

    public void setDispatchAttempt(int dispatchAttempt) {
        this.dispatchAttempt = dispatchAttempt;
    }

    public Long getDeadlineEpochMillis() {
        return deadlineEpochMillis;
    }

    public void setDeadlineEpochMillis(Long deadlineEpochMillis) {
        this.deadlineEpochMillis = deadlineEpochMillis;
    }
}
