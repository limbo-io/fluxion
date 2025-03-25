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

package io.fluxion.remote.core.api.dto;

import io.fluxion.remote.core.constants.Protocol;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Devil
 */
public class WorkerDTO {

    private String appId;

    private Protocol protocol;

    private String host;

    private int port;

    /**
     * 执行器
     */
    private List<WorkerExecutorDTO> executors;

    /**
     * 标签
     */
    private Map<String, Set<String>> tags;

    /**
     * Worker 状态指标
     */
    private WorkerMetricDTO metric;

    private String status;

    private Boolean enabled;

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public void setProtocol(Protocol protocol) {
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

    public List<WorkerExecutorDTO> getExecutors() {
        return executors;
    }

    public void setExecutors(List<WorkerExecutorDTO> executors) {
        this.executors = executors;
    }

    public Map<String, Set<String>> getTags() {
        return tags;
    }

    public void setTags(Map<String, Set<String>> tags) {
        this.tags = tags;
    }

    public WorkerMetricDTO getMetric() {
        return metric;
    }

    public void setMetric(WorkerMetricDTO metric) {
        this.metric = metric;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
