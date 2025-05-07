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

package io.fluxion.remote.core.api.request.broker;

import io.fluxion.remote.core.api.Request;
import io.fluxion.remote.core.api.dto.NodeDTO;
import io.fluxion.remote.core.api.dto.JobMonitorDTO;
import io.fluxion.remote.core.api.response.broker.JobReportResponse;
import io.fluxion.remote.core.constants.JobStatus;

import java.time.LocalDateTime;

/**
 * @author Devil
 * @since 2023/8/3
 */
public class JobReportRequest implements Request<JobReportResponse> {

    private String jobId;

    private NodeDTO workerNode;

    private LocalDateTime reportAt;

    private JobMonitorDTO monitor;

    /**
     * status
     * @see JobStatus
     */
    private String status;

    public NodeDTO getWorkerNode() {
        return workerNode;
    }

    public void setWorkerNode(NodeDTO workerNode) {
        this.workerNode = workerNode;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public LocalDateTime getReportAt() {
        return reportAt;
    }

    public void setReportAt(LocalDateTime reportAt) {
        this.reportAt = reportAt;
    }

    public JobMonitorDTO getMonitor() {
        return monitor;
    }

    public void setMonitor(JobMonitorDTO monitor) {
        this.monitor = monitor;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}
