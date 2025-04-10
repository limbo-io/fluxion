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

package io.fluxion.remote.core.cluster;

import io.fluxion.remote.core.constants.Protocol;

/**
 * @author Devil
 * @since 2022/8/23
 */
public class BaseNode implements Node {

    private final String id;

    private final Protocol protocol;

    private final String host;

    private final int port;

    public BaseNode(Protocol protocol, String host, int port) {
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.id = protocol.value + "://" + host + ":" + port;
    }

    public String id() {
        return id;
    }

    public Protocol protocol() {
        return protocol;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }
}
