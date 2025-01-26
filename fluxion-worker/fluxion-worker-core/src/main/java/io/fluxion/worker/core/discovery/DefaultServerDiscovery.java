/*
 * Copyright 2024-2030 fluxion-io Team (https://github.com/fluxion-io).
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

package io.fluxion.worker.core.discovery;

import io.fluxion.remote.core.api.request.WorkerHeartbeatRequest;
import io.fluxion.remote.core.api.request.WorkerRegisterRequest;
import io.fluxion.remote.core.client.LBClient;
import io.fluxion.remote.core.exception.RpcException;
import io.fluxion.remote.core.heartbeat.HeartbeatPacemaker;
import io.fluxion.worker.core.rpc.BrokerSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

import static io.fluxion.remote.core.constants.BrokerConstant.API_WORKER_HEARTBEAT;
import static io.fluxion.remote.core.constants.BrokerConstant.API_WORKER_REGISTER;
import static io.fluxion.remote.core.constants.WorkerConstant.HEARTBEAT_TIMEOUT_SECOND;

/**
 * @author PengQ
 * @since 0.0.1
 */
public class DefaultServerDiscovery implements ServerDiscovery {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final LBClient client;

    /**
     * manage heartbeat
     */
    private HeartbeatPacemaker heartbeatPacemaker;

    public DefaultServerDiscovery(LBClient client) {
        this.client = client;
    }

    @Override
    public void start() {
        // 注册 todo @pq
        BrokerSender.register(client, API_WORKER_REGISTER, new WorkerRegisterRequest());
        // 心跳管理
        this.heartbeatPacemaker = new HeartbeatPacemaker(() -> {
            try {
                // todo @pq
                BrokerSender.heartbeat(client, API_WORKER_HEARTBEAT, new WorkerHeartbeatRequest());
            } catch (RpcException e) {
                log.warn("Agent send heartbeat failed");
                throw new IllegalStateException("Agent send heartbeat failed", e);
            }
        }, Duration.ofSeconds(HEARTBEAT_TIMEOUT_SECOND));
    }

    @Override
    public void stop() {
        heartbeatPacemaker.stop();
    }
}
