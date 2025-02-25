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

package io.fluxion.remote.core.client;

import io.fluxion.remote.core.api.Request;
import io.fluxion.remote.core.api.Response;
import io.fluxion.remote.core.constants.Protocol;
import io.fluxion.remote.core.lb.LBServer;
import io.fluxion.remote.core.lb.repository.LBServerRepository;
import io.fluxion.remote.core.lb.strategies.LBStrategy;
import io.fluxion.remote.core.lb.strategies.RoundRobinLBStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;

/**
 * @author PengQ
 * @since 0.0.1
 */
public abstract class LBClient {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * 被负载的服务列表
     */
    private final LBServerRepository repository;

    /**
     * 负载均衡策略
     */
    private LBStrategy<LBServer> strategy;

    public LBClient(LBServerRepository repository, LBStrategy<LBServer> strategy) {
        this.repository = repository;
        updateLBStrategy(strategy);
    }

    /**
     * send request and get response by path
     *
     * @param path    path
     * @param request request
     */
    public abstract <R> R call(String path, Request<Response<R>> request);

    /**
     * Protocol used by the client
     */
    public abstract Protocol protocol();

    /**
     * 更新负载均衡策略
     */
    public void updateLBStrategy(LBStrategy<LBServer> strategy) {
        // 默认使用轮询
        if (strategy == null) {
            strategy = new RoundRobinLBStrategy<>();
        }

        this.strategy = strategy;
    }

    protected LBServer select(List<LBServer> servers, String path) {
        return strategy.select(servers, new PathInvocation(path, new HashMap<>()));
    }

    protected List<LBServer> servers() {
        return repository.listAliveServers();
    }

}