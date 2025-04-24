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
 * Node
 * 目前先设计成单协议接口
 * 后续多协议情况下可能为 多协议 + 多端口方式
 * @author Devil
 */
public interface Node {
    /**
     * 唯一标识
     */
    String id();

    Protocol protocol();

    String host();

    int port();

    default String address() {
        return (protocol() == null ? "" : protocol().value + "://")
            + (host() == null ? "" : host() + ":")
            + port();
    }
}
