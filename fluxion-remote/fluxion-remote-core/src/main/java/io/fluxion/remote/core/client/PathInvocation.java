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

import io.fluxion.remote.core.lb.Invocation;

import java.util.Collections;
import java.util.Map;

public class PathInvocation implements Invocation {

    /**
     * 调用接口的 path
     */
    private final String path;

    /**
     * 负载均衡参数
     */
    private final Map<String, String> parameters;

    public PathInvocation(String path, Map<String, String> parameters) {
        this.path = path;
        this.parameters = parameters;
    }


    /**
     * {@inheritDoc}
     *
     * @return
     */
    @Override
    public String targetId() {
        return path;
    }


    /**
     * {@inheritDoc}
     *
     * @return
     */
    @Override
    public Map<String, String> parameters() {
        return Collections.unmodifiableMap(parameters);
    }

}