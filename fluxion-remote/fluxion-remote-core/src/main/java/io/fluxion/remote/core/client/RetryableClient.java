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

package io.fluxion.remote.core.client;

import io.fluxion.remote.core.api.Request;
import io.fluxion.remote.core.lb.LBServer;
import io.fluxion.remote.core.lb.LBStrategy;
import io.fluxion.remote.core.lb.repository.LBServerRepository;
import io.fluxion.remote.core.lb.strategies.RoundRobinLBStrategy;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
public class RetryableClient<S extends LBServer> implements Client {

    private static final Logger log = LoggerFactory.getLogger(RetryableClient.class);

    /**
     * 重试次数
     */
    private int retryTimes;

    /**
     * 被负载的服务列表
     */
    private final LBServerRepository<S> repository;

    /**
     * 负载均衡策略
     */
    private LBStrategy<S> strategy;

    private final Client client;

    public RetryableClient(Client client, LBServerRepository<S> repository) {
        this(client, repository, null);
    }

    public RetryableClient(Client client, int retryTimes, LBServerRepository<S> repository) {
        this(client, retryTimes, repository, null);
    }

    public RetryableClient(Client client, LBServerRepository<S> repository, LBStrategy<S> strategy) {
        this(client, 3, repository, strategy);
    }

    public RetryableClient(Client client, int retryTimes, LBServerRepository<S> repository, LBStrategy<S> strategy) {
        this.client = client;
        this.retryTimes = retryTimes;
        this.repository = repository;
        updateLBStrategy(strategy);
    }


    @Override
    public <R, T extends Request<T>> R call(URL url, T request) {
        List<S> servers = repository.listAliveServers();
        if (CollectionUtils.isEmpty(servers)) {
            throw new IllegalStateException("No alive servers!");
        }
        URI uri;
        try {
            uri = url.toURI();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("url could not be parsed as a URI url:" + url);
        }
        String path = url.getPath();
        for (int i = 1; i <= retryTimes; i++) {
            Optional<S> optional = strategy.select(servers, new PathInvocation(path, new HashMap<>()));
            if (!optional.isPresent()) {
                log.warn("No available alive servers after {} tries from load balancer", i);
                throw new IllegalStateException("Can't get alive server by path=" + path);
            }
            S select = optional.get();
            URL newUrl = null;
            try {
                newUrl = new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    select.url().getHost(),
                    select.url().getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment()
                ).toURL();
                return client.call(newUrl, request);
            } catch (Exception e) {
                log.warn("try {} times... address {} connect fail, try connect new node", i, newUrl, e);
                servers = servers.stream().filter(s -> !s.serverId().equals(select.serverId())).collect(Collectors.toList());
            }

        }
        throw new IllegalStateException("try " + retryTimes + " times... but also fail, throw to out");
    }

    public void updateRetryTimes(int retryTimes) {
        this.retryTimes = retryTimes;
    }

    /**
     * 更新负载均衡策略
     */
    public void updateLBStrategy(LBStrategy<S> strategy) {
        // 默认使用轮询
        if (strategy == null) {
            strategy = new RoundRobinLBStrategy<>();
        }

        this.strategy = strategy;
    }
}
