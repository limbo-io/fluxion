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

package io.fluxion.server.core.tag;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.fluxion.common.constants.CommonConstants;
import lombok.Getter;

public enum TagFilterCondition {

    UNKNOWN(CommonConstants.UNKNOWN, "未知"),

    /**
     * 存在指定名称的标签
     */
    EXISTS("exists", "存在指定名称的标签"),

    /**
     * 不存在指定名称的标签
     */
    NOT_EXISTS("not_exists", "不存在指定名称的标签"),

    /**
     * 存在指定名称的标签且匹配指定值
     */
    MUST_MATCH_VALUE("must_match_value", "存在指定名称的标签且匹配指定值"),

    /**
     * 存在指定名称的标签，且不匹配指定值
     */
    MUST_NOT_MATCH_VALUE("must_not_match_value", "存在指定名称的标签，且不匹配指定值"),

    /**
     * 存在指定名称的标签且匹配正则表达式
     */
    MUST_MATCH_VALUE_REGEX("must_match_value_regex", "存在指定名称的标签且匹配正则表达式"),
    ;

    @JsonValue
    public final String condition;

    @Getter
    public final String desc;

    TagFilterCondition(String condition, String desc) {
        this.condition = condition;
        this.desc = desc;
    }

    public boolean is(String condition) {
        return this.condition.equals(condition);
    }

    @JsonCreator
    public static TagFilterCondition parse(String condition) {
        for (TagFilterCondition tagFilterCondition : values()) {
            if (tagFilterCondition.is(condition)) {
                return tagFilterCondition;
            }
        }
        return UNKNOWN;
    }

}
