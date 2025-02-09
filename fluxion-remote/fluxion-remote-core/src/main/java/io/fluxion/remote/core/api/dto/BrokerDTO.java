/*
 *
 *  * Copyright 2020-2024 Limbo Team (https://github.com/limbo-world).
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * 	http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package io.fluxion.remote.core.api.dto;


import java.util.List;

/**
 * broker节点描述
 *
 * @author Brozen
 * @since 2021-06-16
 */
public class BrokerDTO {

    private String id;

    /**
     * broker节点协议
     */
    private List<ProtocolDTO> protocols;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<ProtocolDTO> getProtocols() {
        return protocols;
    }

    public void setProtocols(List<ProtocolDTO> protocols) {
        this.protocols = protocols;
    }
}
