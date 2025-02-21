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

package io.fluxion.remote.core.api.response;

import io.fluxion.remote.core.api.dto.BrokerTopologyDTO;
import io.fluxion.remote.core.api.dto.NodeDTO;

/**
 * @author PengQ
 * @since 0.0.1
 */
public class WorkerRegisterResponse {
    /**
     * 应用 ID
     */
    private String appId;

    /**
     * 工作节点 ID
     */
    private String workerId;

    /**
     * 当前绑定的broker
     */
    private NodeDTO broker;

    /**
     * broker拓扑信息
     */
    private BrokerTopologyDTO brokerTopology;

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public BrokerTopologyDTO getBrokerTopology() {
        return brokerTopology;
    }

    public void setBrokerTopology(BrokerTopologyDTO brokerTopology) {
        this.brokerTopology = brokerTopology;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public NodeDTO getBroker() {
        return broker;
    }

    public void setBroker(NodeDTO broker) {
        this.broker = broker;
    }
}
