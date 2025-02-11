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


import io.fluxion.remote.core.constants.Protocol;

import java.util.List;
import java.util.Map;

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
     * key 协议
     *
     * @see Protocol
     */
    private Map<String, List<Address>> protocols;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, List<Address>> getProtocols() {
        return protocols;
    }

    public void setProtocols(Map<String, List<Address>> protocols) {
        this.protocols = protocols;
    }

    public static class Address {
        /**
         * 主机名
         */
        private String host;

        /**
         * 服务端口
         */
        private int port;

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
    }
}
