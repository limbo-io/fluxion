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

package io.fluxion.platform.executor.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.fluxion.common.constants.CommonConstants;
import lombok.Getter;

/**
 *
 * @author Brozen
 * @since 2021-05-19
 */
public enum RetryType {

    UNKNOWN(CommonConstants.UNKNOWN, "未知"),
    /**
     * 对于广播/map-map/reduce任务 会重新重试
     */
    ALL("all", "重试所有"),
    /**
     * 对于广播/map-map/reduce任务 只处理失败的任务
     */
    ONLY_FAIL_PART("only_fail_part", "失败部分重试"),
    ;

    @JsonValue
    @Getter
    public final String type;

    @Getter
    public final String desc;

    RetryType(String type, String desc) {
        this.type = type;
        this.desc = desc;
    }

    /**
     * 校验是否是当前状态
     *
     * @param type 待校验值
     */
    public boolean is(RetryType type) {
        return equals(type);
    }

    /**
     * 校验是否是当前状态
     *
     * @param type 待校验状态值
     */
    public boolean is(String type) {
        return this.type.equals(type);
    }

    /**
     * 解析上下文状态值
     */
    @JsonCreator
    public static RetryType parse(String type) {
        for (RetryType retryType : values()) {
            if (retryType.is(type)) {
                return retryType;
            }
        }
        return UNKNOWN;
    }

}
