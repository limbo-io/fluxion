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

package io.fluxion.remote.lb;

import java.net.URL;

/**
 * @author Brozen
 */
public class UrlLBServer implements LBServer {

    /**
     * broker 节点访问的 URL
     */
    private final URL baseUrl;

    public UrlLBServer(URL baseUrl) {
        this.baseUrl = baseUrl;
    }


    /**
     * {@inheritDoc}
     * @return
     */
    @Override
    public String serverId() {
        return baseUrl.toString();
    }


    /**
     * {@inheritDoc}
     * @return
     */
    @Override
    public boolean isAlive() {
        return true;
    }

    @Override
    public URL url() {
        return baseUrl;
    }

}
