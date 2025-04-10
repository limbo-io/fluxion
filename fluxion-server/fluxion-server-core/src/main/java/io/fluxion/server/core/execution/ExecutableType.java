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

package io.fluxion.server.core.execution;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.fluxion.common.constants.CommonConstants;

/**
 * @author Devil
 */
public enum ExecutableType {
    UNKNOWN(CommonConstants.UNKNOWN),
    WORKFLOW(Val.WORKFLOW),
    EXECUTOR(Val.EXECUTOR),
    ;

    public interface Val {
        String WORKFLOW = "workflow";
        String EXECUTOR = "executor";
    }

    @JsonValue
    public final String value;


    ExecutableType(String value) {
        this.value = value;
    }

    public boolean is(String value) {
        return this.value.equals(value);
    }

    @JsonCreator
    public static ExecutableType parse(String value) {
        for (ExecutableType v : values()) {
            if (v.is(value)) {
                return v;
            }
        }
        return UNKNOWN;
    }
}
