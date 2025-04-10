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

import java.time.LocalDateTime;

/**
 * @author Devil
 * @since 2023/8/3
 */
public class JobStartRequest implements Request<Boolean> {

    private String jobId;

    private String workerAddress;

    private LocalDateTime reportAt;

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getWorkerAddress() {
        return workerAddress;
    }

    public void setWorkerAddress(String workerAddress) {
        this.workerAddress = workerAddress;
    }

    public LocalDateTime getReportAt() {
        return reportAt;
    }

    public void setReportAt(LocalDateTime reportAt) {
        this.reportAt = reportAt;
    }
}
