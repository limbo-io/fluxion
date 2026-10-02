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

package io.fluxion.worker.core.job;

import io.fluxion.remote.core.constants.ExecuteMode;
import io.fluxion.remote.core.constants.JobStatus;

import java.time.Instant;

/**
 * @author Devil
 * @since 2021/7/24
 */
public class Job {

    private String id;

    private int dispatchAttempt;

    private ExecuteMode executeMode;

    /**
     * 执行器的名称
     */
    private String executorName;

    /**
     * 状态
     */
    private JobStatus status = JobStatus.INITED;

    /**
     * Job 的原始计划触发时刻（见 CONTEXT.md「Job 时间语义」）：调度 Execution 的首批 Job
     * 继承 Execution.triggerAt；后续 Workflow 节点是自身就绪时刻。接收时刻由 Worker 本机自行取得。
     */
    private Instant triggerAt;

    private String result;

    private String errorMsg;

    public Instant getTriggerAt() {
        return triggerAt;
    }

    public void setTriggerAt(Instant triggerAt) {
        this.triggerAt = triggerAt;
    }

    public ExecuteMode getExecuteMode() {
        return executeMode;
    }

    public void setExecuteMode(ExecuteMode executeMode) {
        this.executeMode = executeMode;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getDispatchAttempt() {
        return dispatchAttempt;
    }

    public void setDispatchAttempt(int dispatchAttempt) {
        this.dispatchAttempt = dispatchAttempt;
    }

    public String getExecutorName() {
        return executorName;
    }

    public void setExecutorName(String executorName) {
        this.executorName = executorName;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

}
