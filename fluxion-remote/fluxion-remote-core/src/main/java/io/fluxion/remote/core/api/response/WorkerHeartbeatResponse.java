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

package io.fluxion.remote.core.api.response;

import io.fluxion.remote.core.api.dto.BrokerTopologyDTO;

/**
 * @author PengQ
 * @since 0.0.1
 */
public class WorkerHeartbeatResponse {

    /**
     * broker拓扑信息
     */
    private BrokerTopologyDTO brokerTopology;

    public BrokerTopologyDTO getBrokerTopology() {
        return brokerTopology;
    }

    public void setBrokerTopology(BrokerTopologyDTO brokerTopology) {
        this.brokerTopology = brokerTopology;
    }


}
