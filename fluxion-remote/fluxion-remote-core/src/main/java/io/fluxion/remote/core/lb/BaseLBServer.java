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

package io.fluxion.remote.core.lb;

import io.fluxion.remote.core.cluster.Node;

/**
 * @author Brozen
 */
public class BaseLBServer implements LBServer {

    private final Node node;

    public BaseLBServer(Node node) {
        this.node = node;
    }


    /**
     * {@inheritDoc}
     *
     * @return
     */
    @Override
    public String serverId() {
        return node.id();
    }


    /**
     * {@inheritDoc}
     *
     * @return
     */
    @Override
    public boolean isAlive() {
        return true;
    }

    @Override
    public String host() {
        return node.getHost();
    }

    @Override
    public int port() {
        return node.getPort();
    }

}
