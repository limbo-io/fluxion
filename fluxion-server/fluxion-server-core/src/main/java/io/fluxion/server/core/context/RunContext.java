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

package io.fluxion.server.core.context;

/**
 * 运行上下文
 *
 * @author Devil
 */
public class RunContext {
    /**
     * 执行 id
     */
    private final String executionId;

    private RunContext(String executionId) {
        this.executionId = executionId;
    }

    public static RunContext of(String executionId) {
        return new RunContext(executionId);
    }

    public String executionId() {
        return executionId;
    }

//    @Getter
//    public static class Cache {
//
//        private Map<?, ?> globalVars = null;
//
//        private Map<String, String> envVars = null;
//    }
}