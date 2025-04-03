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

package io.fluxion.remote.core.api.request;

import io.fluxion.remote.core.api.Request;
import io.fluxion.remote.core.constants.ExecuteResult;

import java.time.LocalDateTime;

/**
 * @author Devil
 * @since 2023/8/3
 */
public class JobFeedbackRequest implements Request<Boolean> {

    private final String jobId;

    private final String workerAddress;

    private final LocalDateTime reportAt;
    /**
     * @see ExecuteResult
     */
    private final String executeResult;

    /**
     * 执行失败时候返回的信息
     */
    private String errorMsg;

    public JobFeedbackRequest(String jobId, String workerAddress, LocalDateTime reportAt, String executeResult) {
        this.jobId = jobId;
        this.workerAddress = workerAddress;
        this.reportAt = reportAt;
        this.executeResult = executeResult;
    }

    public JobFeedbackRequest(String jobId, String workerAddress, LocalDateTime reportAt, String executeResult, String errorMsg) {
        this.jobId = jobId;
        this.workerAddress = workerAddress;
        this.reportAt = reportAt;
        this.executeResult = executeResult;
        this.errorMsg = errorMsg;
    }

    public String getExecuteResult() {
        return executeResult;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public LocalDateTime getReportAt() {
        return reportAt;
    }

    public String getWorkerAddress() {
        return workerAddress;
    }

    public String getJobId() {
        return jobId;
    }
}
