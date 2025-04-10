package io.fluxion.remote.core.api.request;

import io.fluxion.remote.core.api.Request;

import java.time.LocalDateTime;

public class TaskFailRequest implements Request<Boolean> {

    private String jobId;

    private String taskId;

    private String workerAddress;

    private LocalDateTime reportAt;

    /**
     * 执行失败时候返回的信息
     */
    private String errorMsg;

    private String errorStackTrace;

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public LocalDateTime getReportAt() {
        return reportAt;
    }

    public void setReportAt(LocalDateTime reportAt) {
        this.reportAt = reportAt;
    }

    public String getWorkerAddress() {
        return workerAddress;
    }

    public void setWorkerAddress(String workerAddress) {
        this.workerAddress = workerAddress;
    }

    public String getErrorStackTrace() {
        return errorStackTrace;
    }

    public void setErrorStackTrace(String errorStackTrace) {
        this.errorStackTrace = errorStackTrace;
    }
}