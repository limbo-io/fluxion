package io.fluxion.remote.core.api.request;

import io.fluxion.remote.core.api.Request;

import java.time.LocalDateTime;

/**
 * @author Devil
 * @since 2023/8/3
 */
public class JobSuccessRequest implements Request<Boolean> {

    private String jobId;

    private String workerAddress;

    private LocalDateTime reportAt;

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

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }
}