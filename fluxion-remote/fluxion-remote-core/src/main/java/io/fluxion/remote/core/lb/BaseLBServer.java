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

package io.fluxion.remote.core.lb;

/**
 * @author Brozen
 */
public class BaseLBServer implements LBServer {

    private final String host;

    private final int port;

    public BaseLBServer(String host, int port) {
        this.host = host;
        this.port = port;
    }


    /**
     * {@inheritDoc}
     *
     * @return
     */
    @Override
    public String serverId() {
        return host + ":" + port;
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
        return host;
    }

    @Override
    public int port() {
        return port;
    }

}
