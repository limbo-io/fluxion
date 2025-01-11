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

package io.fluxion.remote.lb.strategies;

import io.fluxion.remote.lb.AbstractLBStrategy;
import io.fluxion.remote.lb.Invocation;
import io.fluxion.remote.lb.LBServer;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * todo
 * @author Brozen
 */
public class AppointLBStrategy<S extends LBServer> extends AbstractLBStrategy<S> {

    /**
     * 通过节点RPC通信URL指定负载均衡节点
     */
    public static final String PARAM_BY_URL = "appoint.byUrl";

    /**
     * 通过节点 ID 指定负载均衡节点
     */
    public static final String PARAM_BY_SERVER_ID = "appoint.byServerId";


    /**
     * 从调用参数中解析指定节点的类型。
     */
    private boolean filter(S server, Invocation invocation) {
        if (server == null || server.url() == null) {
            return false;
        }
        Map<String, String> params = invocation.loadBalanceParameters();
        String byServerId = params.get(PARAM_BY_SERVER_ID);
        if (StringUtils.isNotBlank(byServerId) && StringUtils.equals(server.serverId(), byServerId)) {
            return true;
        }

        String byUrl = params.get(PARAM_BY_URL);
        if (StringUtils.isNotBlank(byUrl) && server.url().toString().equals(byUrl)) {
            return true;
        }

        return false;
    }


    /**
     * {@inheritDoc}
     *
     * @param servers
     * @param invocation
     * @return
     */
    @Override
    protected Optional<S> doSelect(List<S> servers, Invocation invocation) {
        if (CollectionUtils.isEmpty(servers)) {
            return Optional.empty();
        }

        return servers.stream()
                .filter(server -> filter(server, invocation))
                .findFirst();
    }

}
