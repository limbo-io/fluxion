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

package io.fluxion.server.core.broker;

import io.fluxion.remote.core.api.Request;
import io.fluxion.remote.core.api.request.BrokerPingRequest;

import static io.fluxion.remote.core.constants.BrokerRemoteConstant.API_BROKER_PING;

/**
 * @author Devil
 * @date 2025/1/10
 */
public class BrokerContext {

    private static Broker broker;

    public static Broker broker() {
        return broker;
    }

    public static void initialize(Broker broker) {
        BrokerContext.broker = broker;
    }

    public static <R> R call(String path, String host, int port, Request<R> request) {
        return broker().client().call(path, host, port, request).getData();
    }

    public static Boolean ping(String host, int port) {
        BrokerNode node = broker().node();
        if (node.host().equals(host) && node.port() == port) {
            return true; // 当前节点直接返回
        }
        return call(API_BROKER_PING, host, port, new BrokerPingRequest());
    }

}
