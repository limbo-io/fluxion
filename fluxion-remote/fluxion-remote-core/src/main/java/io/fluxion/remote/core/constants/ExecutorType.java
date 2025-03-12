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

package io.fluxion.remote.core.constants;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.fluxion.common.constants.CommonConstants;

/**
 * @author Brozen
 * @since 2021-05-19
 */
public enum ExecutorType {

    UNKNOWN(CommonConstants.UNKNOWN_STR),
    /**
     * 单机任务
     * 给一个节点下发的任务
     */
    STANDALONE("standalone"),
    /**
     * 广播任务
     * 给每个可选中节点下发任务
     */
    BROADCAST("broadcast"),
    /**
     * MapReduce任务
     * 拆分任务->处理分片->最终处理
     */
    MAP_REDUCE("map_reduce"),
    ;

    @JsonValue
    public final String type;

    ExecutorType(String type) {
        this.type = type;
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
    public static ExecutorType parse(String type) {
        for (ExecutorType t : values()) {
            if (t.is(type)) {
                return t;
            }
        }
        return UNKNOWN;
    }

}
