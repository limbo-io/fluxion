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

package io.fluxion.remote.core.lb.repository;

import io.fluxion.remote.core.lb.LBServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Brozen
 */
public class EmbeddedLBServerRepository implements LBServerRepository {

    /**
     * 被负载的服务列表
     */
    private volatile List<LBServer> servers;


    public EmbeddedLBServerRepository(List<LBServer> servers) {
        updateServers(servers);
    }


    /**
     * {@inheritDoc}
     * @param servers 服务列表
     */
    @Override
    public void updateServers(List<LBServer> servers) {
        this.servers = Collections.unmodifiableList(new ArrayList<>(servers));
    }

    /**
     * {@inheritDoc}
     * @return
     */
    @Override
    public List<LBServer> listAliveServers() {
        return this.servers.stream()
                .filter(LBServer::isAlive)
                .collect(Collectors.toList());
    }


    /**
     * {@inheritDoc}
     * @return
     */
    @Override
    public List<LBServer> listAllServers() {
        return this.servers;
    }

}
