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

package io.fluxion.worker.core.remote;

import io.fluxion.remote.core.api.cluster.Node;
import io.fluxion.remote.core.api.request.WorkerHeartbeatRequest;
import io.fluxion.remote.core.api.request.WorkerRegisterRequest;
import io.fluxion.remote.core.api.response.WorkerHeartbeatResponse;
import io.fluxion.remote.core.api.response.WorkerRegisterResponse;
import io.fluxion.remote.core.client.Client;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author PengQ
 * @since 0.0.1
 */
public class BrokerSender {

    public static WorkerRegisterResponse register(Client client, Node node, String path, WorkerRegisterRequest request) {
        return client.call(url(node, path), request);
    }

    public static WorkerHeartbeatResponse heartbeat(Client client, Node node, String path, WorkerHeartbeatRequest request) {
        return client.call(url(node, path), request);
    }

    private static URL url(Node node, String path) {
        try {
            return new URL(node.getProtocol().getValue(), node.getHost(), node.getPort(), path);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

}
