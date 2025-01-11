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

package io.fluxion.core.version.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.fluxion.common.constants.CommonConstants;

/**
 * @author Devil
 */
public enum VersionRefType {
    UNKNOWN(CommonConstants.UNKNOWN),
    FLOW("flow"),
    TRIGGER("trigger"),
    ;

    @JsonValue
    public final String type;


    VersionRefType(String type) {
        this.type = type;
    }

    public boolean is(String type) {
        return this.type.equals(type);
    }

    @JsonCreator
    public static VersionRefType parse(String type) {
        for (VersionRefType versionRefType : values()) {
            if (versionRefType.is(type)) {
                return versionRefType;
            }
        }
        return UNKNOWN;
    }
}
