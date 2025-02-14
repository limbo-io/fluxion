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

package io.fluxion.remote.core.api.dto;

import java.util.Collections;
import java.util.List;

/**
 * @author PengQ
 * @since 0.0.1
 */
public class BrokerTopologyDTO {

    /**
     * broker节点列表，主从模式下，列表中仅包括一个主节点
     */
    private List<NodeDTO> brokers = Collections.emptyList();

    public List<NodeDTO> getBrokers() {
        return brokers;
    }

    public void setBrokers(List<NodeDTO> brokers) {
        this.brokers = brokers;
    }
}
