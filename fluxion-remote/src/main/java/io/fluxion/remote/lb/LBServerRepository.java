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

import java.util.List;

/**
 * LB服务管理
 *
 * @author Brozen
 */
public interface LBServerRepository<S extends LBServer> {

    /**
     * 更新被的服务列表
     * @param servers 服务列表
     */
    void updateServers(List<S> servers);


    /**
     * 列出所有存活的服务
     */
    List<S> listAliveServers();


    /**
     * 列出所有服务
     */
    List<S> listAllServers();

}
