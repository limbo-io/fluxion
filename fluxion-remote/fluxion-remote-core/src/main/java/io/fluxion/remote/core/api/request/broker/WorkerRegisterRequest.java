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
import io.fluxion.remote.core.api.Response;
import io.fluxion.remote.core.api.dto.SystemInfoDTO;
import io.fluxion.remote.core.api.dto.WorkerExecutorDTO;
import io.fluxion.remote.core.api.dto.WorkerTagDTO;
import io.fluxion.remote.core.api.response.broker.WorkerRegisterResponse;
import io.fluxion.remote.core.constants.Protocol;

import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * @author Devil
 * @since 2023/8/3
 */
public class WorkerRegisterRequest implements Request<Response<WorkerRegisterResponse>> {

    /**
     * 应用名称
     */
    private String appName;

    /**
     * @see Protocol
     */
    private String protocol;

    private String host;

    private int port;

    /**
     * worker 的标签
     */
    private List<WorkerTagDTO> tags;

    /**
     * 执行器
     */
    @NotEmpty(message = "worker executor can't be empty")
    private List<WorkerExecutorDTO> executors;

    private SystemInfoDTO systemInfo;

    private int availableQueueNum;

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public SystemInfoDTO getSystemInfo() {
        return systemInfo;
    }

    public void setSystemInfo(SystemInfoDTO systemInfo) {
        this.systemInfo = systemInfo;
    }

    public List<WorkerTagDTO> getTags() {
        return tags;
    }

    public void setTags(List<WorkerTagDTO> tags) {
        this.tags = tags;
    }

    public List<WorkerExecutorDTO> getExecutors() {
        return executors;
    }

    public void setExecutors(List<WorkerExecutorDTO> executors) {
        this.executors = executors;
    }

    public int getAvailableQueueNum() {
        return availableQueueNum;
    }

    public void setAvailableQueueNum(int availableQueueNum) {
        this.availableQueueNum = availableQueueNum;
    }
}
