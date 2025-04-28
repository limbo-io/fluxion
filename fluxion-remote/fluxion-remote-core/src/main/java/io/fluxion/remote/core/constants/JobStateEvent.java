/*
 * Copyright 2025-2030 limbo-io Team (https://github.com/limbo-io).
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

package io.fluxion.remote.core.constants;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.fluxion.common.constants.CommonConstants;

/**
 * @author Devil
 */
public enum JobStateEvent {
    UNKNOWN(CommonConstants.UNKNOWN),
    START("start"),
    RUN_SUCCESS("run_success"),
    RUN_FAIL("run_fail"),
    ;

    @JsonValue
    public final String value;


    JobStateEvent(String value) {
        this.value = value;
    }

    public boolean is(String value) {
        return this.value.equals(value);
    }

    @JsonCreator
    public static JobStateEvent parse(String value) {
        for (JobStateEvent v : values()) {
            if (v.is(value)) {
                return v;
            }
        }
        return UNKNOWN;
    }

}
