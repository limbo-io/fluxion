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

package io.fluxion.server.infrastructure.tag;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.fluxion.common.constants.CommonConstants;
import lombok.Getter;

public enum TagFilterCondition {

    UNKNOWN(CommonConstants.UNKNOWN, "未知"),

    /**
     * 存在指定名称的标签
     */
    EXISTS(1, "存在指定名称的标签"),

    /**
     * 不存在指定名称的标签
     */
    NOT_EXISTS(2, "不存在指定名称的标签"),

    /**
     * 存在指定名称的标签且匹配指定值
     */
    MUST_MATCH_VALUE(3, "存在指定名称的标签且匹配指定值"),

    /**
     * 存在指定名称的标签，且不匹配指定值
     */
    MUST_NOT_MATCH_VALUE(4, "存在指定名称的标签，且不匹配指定值"),

    /**
     * 存在指定名称的标签且匹配正则表达式
     */
    MUST_MATCH_VALUE_REGEX(5, "存在指定名称的标签且匹配正则表达式"),
    ;

    @JsonValue
    public final int value;

    @Getter
    public final String desc;

    TagFilterCondition(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public boolean is(Number value) {
        return value != null && value.intValue() == this.value;
    }

    @JsonCreator
    public static TagFilterCondition parse(Number value) {
        for (TagFilterCondition v : values()) {
            if (v.is(value)) {
                return v;
            }
        }
        return UNKNOWN;
    }

}
