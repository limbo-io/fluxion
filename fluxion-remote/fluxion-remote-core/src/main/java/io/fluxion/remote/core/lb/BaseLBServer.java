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

import io.fluxion.remote.core.constants.Protocol;

import java.net.URL;

/**
 * @author Brozen
 */
public class BaseLBServer implements LBServer {

    private final URL url;

    public BaseLBServer(URL baseUrl) {
        this.url = baseUrl;
    }


    /**
     * {@inheritDoc}
     *
     * @return
     */
    @Override
    public String serverId() {
        return url.toString();
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
    public Protocol protocol() {
        return Protocol.parse(url.getProtocol());
    }

    @Override
    public String host() {
        return url.getHost();
    }

    @Override
    public int port() {
        return url.getPort();
    }

}
