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

package io.fluxion.remote.core.api.request.broker;

import io.fluxion.remote.core.api.Request;
import io.fluxion.remote.core.api.dto.SystemInfoDTO;
import io.fluxion.remote.core.api.response.broker.WorkerHeartbeatResponse;

/**
 * worker send heartbeat to broker
 *
 * @author Devil
 * @since 2023/8/3
 */
public class WorkerHeartbeatRequest implements Request<WorkerHeartbeatResponse> {

    private String appId;

    private String workerId;

    private SystemInfoDTO systemInfo;

    private int availableQueueNum;

    private long heartbeatAt;

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public SystemInfoDTO getSystemInfo() {
        return systemInfo;
    }

    public void setSystemInfo(SystemInfoDTO systemInfo) {
        this.systemInfo = systemInfo;
    }

    public int getAvailableQueueNum() {
        return availableQueueNum;
    }

    public void setAvailableQueueNum(int availableQueueNum) {
        this.availableQueueNum = availableQueueNum;
    }

    public long getHeartbeatAt() {
        return heartbeatAt;
    }

    public void setHeartbeatAt(long heartbeatAt) {
        this.heartbeatAt = heartbeatAt;
    }
}
