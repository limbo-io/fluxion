/*
 * Copyright 2025-2030 Fluxion Team (https://github.com/Fluxion-io).
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

package io.fluxion.remote.core.lb;

import io.fluxion.remote.core.constants.Protocol;

/**
 * 被负载均衡的服务
 *
 * @author Brozen
 */
public interface LBServer {

    /**
     * 当前服务的唯一 ID
     */
    String serverId();

    /**
     * 当前服务是否存活可用
     */
    boolean isAlive();

    /**
     * 协议
     */
    Protocol protocol();

    /**
     * 服务地址
     */
    String host();

    /**
     * 端口号
     */
    int port();

}
