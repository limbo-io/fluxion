/*
 *
 *  * Copyright 2020-2024 fluxion Team (https://github.com/fluxion-io).
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * 	http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package io.fluxion.server.core.executor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.fluxion.common.constants.CommonConstants;

/**
 *
 * @author Brozen
 * @since 2021-05-19
 */
public enum ExecuteType {

    UNKNOWN(CommonConstants.UNKNOWN),
    /**
     * 单机任务
     * 给一个节点下发的任务
     */
    STANDALONE(1),
    /**
     * 广播任务
     * 给每个可选中节点下发任务
     */
    BROADCAST(2),
    /**
     * Map任务
     * 并行任务 拆分任务->处理分片
     */
    MAP(3),
    /**
     * MapReduce任务
     * 拆分任务->处理分片->最终处理
     */
    MAP_REDUCE(4),
    ;

    @JsonValue
    public final int type;

    ExecuteType(int type) {
        this.type = type;
    }

    /**
     * 校验是否是当前状态
     *
     * @param type 待校验值
     */
    public boolean is(ExecuteType type) {
        return equals(type);
    }

    /**
     * 校验是否是当前状态
     *
     * @param type 待校验状态值
     */
    public boolean is(Number type) {
        return type != null && type.intValue() == this.type;
    }

    /**
     * 解析上下文状态值
     */
    @JsonCreator
    public static ExecuteType parse(Number type) {
        for (ExecuteType t : values()) {
            if (t.is(type)) {
                return t;
            }
        }
        return UNKNOWN;
    }

}
