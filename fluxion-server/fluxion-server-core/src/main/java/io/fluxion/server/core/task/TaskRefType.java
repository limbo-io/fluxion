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

package io.fluxion.server.core.task;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.fluxion.common.constants.CommonConstants;

/**
 * @author Devil
 */
public enum TaskRefType {
    UNKNOWN(CommonConstants.UNKNOWN),
    FLOW_NODE(1),
    ;

    @JsonValue
    public final int value;


    TaskRefType(int value) {
        this.value = value;
    }

    public boolean is(Number value) {
        return value != null && value.intValue() == this.value;
    }

    @JsonCreator
    public static TaskRefType parse(Number value) {
        for (TaskRefType v : values()) {
            if (v.is(value)) {
                return v;
            }
        }
        return UNKNOWN;
    }
}
