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

package io.fluxion.remote.core.lb.strategies;

import io.fluxion.remote.core.lb.Invocation;
import io.fluxion.remote.core.lb.LBServer;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * @author Brozen
 */
public class AppointLBStrategy<S extends LBServer> extends AbstractLBStrategy<S> {

    /**
     * 节点ID
     */
    public static final String PARAM_BY_SERVER_ID = "appoint.serverId";

    /**
     * 通过节点RPC通信地址指定负载均衡节点
     */
    public static final String PARAM_BY_SERVER_ADDRESS = "appoint.serverAddress";


    /**
     * 从调用参数中解析指定节点的类型。
     */
    private boolean filter(S server, Invocation invocation) {
        if (server == null) {
            return false;
        }
        Map<String, String> params = invocation.parameters();
        String serverId = params.get(PARAM_BY_SERVER_ID);
        if (StringUtils.isNotBlank(serverId) && StringUtils.equals(server.serverId(), serverId)) {
            return true;
        }
        String address = params.get(PARAM_BY_SERVER_ADDRESS);
        return StringUtils.isNotBlank(address) && StringUtils.equals(address, server.host() + ":" + server.port());
    }


    /**
     * {@inheritDoc}
     *
     * @param servers
     * @param invocation
     * @return
     */
    @Override
    protected S doSelect(List<S> servers, Invocation invocation) {
        if (CollectionUtils.isEmpty(servers)) {
            return null;
        }

        return servers.stream()
            .filter(server -> filter(server, invocation))
            .findFirst().orElse(null);
    }

}
