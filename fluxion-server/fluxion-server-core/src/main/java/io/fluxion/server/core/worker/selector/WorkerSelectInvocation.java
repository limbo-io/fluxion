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

package io.fluxion.server.core.worker.selector;

import io.fluxion.remote.core.lb.Invocation;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Brozen
 * @since 2023-02-01
 */
public class WorkerSelectInvocation implements Invocation {

    private String executorName;

    private Map<String, String> lbParameters;

    private static final String LB_PREFIX = "worker.lb.";

    public WorkerSelectInvocation(String executorName, Map<String, Object> attributes) {
        this.executorName = executorName;
        this.lbParameters = new HashMap<>();
        init(attributes);
    }

    public void init(Map<String, Object> attributes) {
        if (attributes == null) {
            return;
        }
        attributes.entrySet().stream()
                .filter(entry -> StringUtils.isNotBlank(entry.getKey()) && entry.getValue() != null)
                .filter(entry -> entry.getKey().startsWith(LB_PREFIX))
                .filter(entry -> entry.getValue() instanceof String)
                .forEach(entry -> lbParameters.put(entry.getKey().replace(LB_PREFIX, ""), (String) entry.getValue()));
    }

    @Override
    public String invokeId() {
        return executorName;
    }

    @Override
    public Map<String, String> loadBalanceParameters() {
        return Collections.unmodifiableMap(lbParameters);
    }
}
