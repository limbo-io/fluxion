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

import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author Devil
 */
public interface Client {
    /**
     * send request and get response by url
     *
     * @param url     url
     * @param request request
     */
    <R> Response<R> call(URL url, Request<R> request);

    /**
     * Protocol used by the client
     */
    Protocol protocol();

    /**
     * send request by path and node
     *
     * @param path    path
     * @param host    server host
     * @param port    server port
     * @param request request
     */
    default <R> Response<R> call(String path, String host, int port, Request<R> request) {
        return call(url(path, host, port), request);
    }

    default URL url(String path, String host, int port) {
        try {
            return new URL(protocol().getValue(), host, port, path);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

}
