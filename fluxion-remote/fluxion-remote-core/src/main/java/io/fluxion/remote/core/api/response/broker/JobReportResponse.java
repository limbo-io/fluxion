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

package io.fluxion.remote.core.api.response.broker;

import io.fluxion.remote.core.api.dto.NodeDTO;
import io.fluxion.remote.core.constants.JobStatus;
import io.fluxion.remote.core.constants.TaskStatus;

/**
 * 失败原因：
 * 1. job为空
 * 2. worker节点变化
 * 3. 前置状态不一致
 * @author Devil
 */
public class JobReportResponse {
    /**
     * 是否成功
     */
    private boolean success;
    /**
     * 当前绑定的工作节点
     */
    private NodeDTO workerNode;

    /**
     * status
     * @see JobStatus
     */
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public NodeDTO getWorkerNode() {
        return workerNode;
    }

    public void setWorkerNode(NodeDTO workerNode) {
        this.workerNode = workerNode;
    }
}
