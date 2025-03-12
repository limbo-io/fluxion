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

import io.fluxion.common.utils.MD5Utils;
import io.fluxion.remote.core.cluster.BaseNode;
import io.fluxion.remote.core.constants.Protocol;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Devil
 */
public class BrokerNode extends BaseNode {

    private final String id;

    private final AtomicInteger load;

    public BrokerNode(Protocol protocol, String host, int port, Integer load) {
        super(protocol, host, port);
        this.id = MD5Utils.md5(host + ":" + port);
        this.load = new AtomicInteger(load == null ? 0 : load);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return "BrokerNode{" +
            "id='" + id + '\'' +
            ", protocol=" + protocol() +
            ", host=" + host() +
            ", port=" + port() +
            ", load=" + load +
            '}';
    }

    public Integer load() {
        return load.get();
    }

    public void loadIncr(LoadType type) {
        if (type == null) {
            return;
        }
        load.addAndGet(type.load());
    }

    public void loadDecr(LoadType type) {
        if (type == null) {
            return;
        }
        load.addAndGet(-type.load());
    }

    public enum LoadType {
        WORKER(100000),
        TASK(1000),
        ;

        private final int load;

        LoadType(int load) {
            this.load = load;
        }

        public int load() {
            return load;
        }
    }

}
