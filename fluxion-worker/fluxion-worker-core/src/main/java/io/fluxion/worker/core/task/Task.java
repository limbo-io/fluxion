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

package io.fluxion.worker.core.task;

import io.fluxion.remote.core.constants.TaskStatus;

import java.time.LocalDateTime;

/**
 * @author Devil
 */
public class Task {

    private final String id;

    private final String jobId;

    /**
     * 管理节点地址
     */
    private String remoteAddress;

    /**
     * 执行节点地址
     */
    private String workerAddress;

    /**
     * 状态
     */
    private TaskStatus status;

    /**
     * 预期触发时间
     */
    private LocalDateTime triggerAt;

    /**
     * 开始时间
     */
    private LocalDateTime startAt;

    /**
     * 结束时间
     */
    private LocalDateTime endAt;

    /**
     * 上次上报时间
     */
    private LocalDateTime lastReportAt;

    private String result;

    private String errorMsg;

    private String errorStackTrace;

    /**
     * 下发失败次数
     */
    private int dispatchFailTimes = 0;

    public Task(String id, String jobId) {
        this.id = id;
        this.jobId = jobId;
    }

    public String getJobId() {
        return jobId;
    }

    public String getId() {
        return id;
    }

    public String getWorkerAddress() {
        return workerAddress;
    }

    public void setWorkerAddress(String workerAddress) {
        this.workerAddress = workerAddress;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public LocalDateTime getTriggerAt() {
        return triggerAt;
    }

    public void setTriggerAt(LocalDateTime triggerAt) {
        this.triggerAt = triggerAt;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public void setEndAt(LocalDateTime endAt) {
        this.endAt = endAt;
    }

    public LocalDateTime getLastReportAt() {
        return lastReportAt;
    }

    public void setLastReportAt(LocalDateTime lastReportAt) {
        this.lastReportAt = lastReportAt;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public String getErrorStackTrace() {
        return errorStackTrace;
    }

    public void setErrorStackTrace(String errorStackTrace) {
        this.errorStackTrace = errorStackTrace;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public int getDispatchFailTimes() {
        return dispatchFailTimes;
    }

    public void dispatchFail() {
        dispatchFailTimes++;
    }
}
