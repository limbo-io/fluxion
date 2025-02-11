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
import io.fluxion.remote.core.lb.repository.LBServerRepository;
import io.fluxion.remote.core.lb.strategies.LBStrategy;
import org.apache.commons.collections4.CollectionUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
public class RetryableClient extends AbstractLBClient implements Client {

    /**
     * 重试次数
     */
    private int retryTimes;

    private final Client client;

    private RetryableClient(Client client, int retryTimes, LBServerRepository repository, LBStrategy<LBServer> strategy) {
        super(repository, strategy);
        this.client = client;
        this.retryTimes = retryTimes;
    }


    @Override
    public <R, T extends Request<R>> R call(URL url, T request) {
        List<LBServer> servers = servers();
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
            LBServer server = select(servers, path);
            if (server == null) {
                log.warn("No available alive servers after {} tries from load balancer", i);
                throw new IllegalStateException("Can't get alive server by path=" + path);
            }
            URL newUrl = null;
            try {
                newUrl = new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    server.host(),
                    server.port(),
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment()
                ).toURL();
                return client.call(newUrl, request);
            } catch (Exception e) {
                log.warn("try {} times... address {} connect fail, try connect new node", i, newUrl, e);
                servers = servers.stream().filter(s -> !s.serverId().equals(server.serverId())).collect(Collectors.toList());
            }

        }
        throw new IllegalStateException("try " + retryTimes + " times... but also fail, throw to out");
    }

    @Override
    public <R, T extends Request<R>> R call(String path, T request) {
        return null;
    }

    public void updateRetryTimes(int retryTimes) {
        this.retryTimes = retryTimes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Client client;
        private int retryTimes = 3;
        private LBServerRepository repository;
        private LBStrategy<LBServer> strategy;

        public Builder client(Client client) {
            this.client = client;
            return this;
        }

        public Builder retryTimes(int retryTimes) {
            this.retryTimes = retryTimes;
            return this;
        }

        public Builder repository(LBServerRepository repository) {
            this.repository = repository;
            return this;
        }

        public Builder strategy(LBStrategy<LBServer> strategy) {
            this.strategy = strategy;
            return this;
        }

        public RetryableClient build() {
            return new RetryableClient(client, retryTimes, repository, strategy);
        }

    }

}
