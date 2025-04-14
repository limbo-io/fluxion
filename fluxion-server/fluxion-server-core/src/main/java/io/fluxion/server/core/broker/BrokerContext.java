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

import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.remote.core.api.Request;
import io.fluxion.remote.core.api.Response;
import io.fluxion.remote.core.api.request.BrokerPingRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.fluxion.remote.core.constants.BrokerRemoteConstant.API_BROKER_PING;

/**
 * @author Devil
 * @date 2025/1/10
 */
public class BrokerContext {

    private static final Logger log = LoggerFactory.getLogger(BrokerContext.class);

    private static Broker broker;

    public static Broker broker() {
        return broker;
    }

    public static void initialize(Broker broker) {
        BrokerContext.broker = broker;
    }

    public static <R> Response<R> call(String path, String host, int port, Request<R> request) {
        Response<R> response = broker().client().call(path, host, port, request);
        if (log.isDebugEnabled()) {
            log.debug("Remote Call host: {} port: {} request:{} response:{}", host, port, JacksonUtils.toJSONString(request), response);
        }
        return response;
    }

    public static Boolean ping(String host, int port) {
        BrokerNode node = broker().node();
        if (node.host().equals(host) && node.port() == port) {
            return true; // 当前节点直接返回
        }
        Response<Boolean> response = call(API_BROKER_PING, host, port, new BrokerPingRequest());
        return response.getData();
    }

}
