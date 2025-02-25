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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

/**
 * Call url with retry
 *
 * @author Devil
 */
public class RetryableClient implements Client {

    private static final Logger log = LoggerFactory.getLogger(RetryableClient.class);

    /**
     * 重试次数
     */
    private int retryTimes;

    private final Client client;

    private RetryableClient(Client client, int retryTimes) {
        this.client = client;
        this.retryTimes = retryTimes;
    }

    @Override
    public <R> R call(URL url, Request<Response<R>> request) {
        for (int i = 1; i <= retryTimes; i++) {
            try {
                return client.call(url, request);
            } catch (Exception e) {
                log.warn("try {} times... address {} connect fail, try connect new node", i, url, e);
            }

        }
        throw new IllegalStateException("try " + retryTimes + " times... but also fail, throw to out");
    }

    @Override
    public Protocol protocol() {
        return client.protocol();
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

        public Builder client(Client client) {
            this.client = client;
            return this;
        }

        public Builder retryTimes(int retryTimes) {
            this.retryTimes = retryTimes;
            return this;
        }

        public RetryableClient build() {
            return new RetryableClient(client, retryTimes);
        }

    }

}
