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
public enum TaskStatus {
    UNKNOWN(CommonConstants.UNKNOWN),
    /**
     * 已经创建 等待下发
     */
    SCHEDULING(1),
    /**
     * 接收到 下发worker的反馈
     */
    RECEIVED(2),
    /**
     * 接收到 worker的执行反馈
     */
    EXECUTING(3),
    SUCCEED(4),
    FAILED(5), // worker拒绝，进入容错策略 失败次数不增加 TERMINATED 作业被手动终止 不再增加一个状态 而是写入 errMsg
    /**
     * 重试 等待下发
     */
    RE_SCHEDULING(6),
    ;

    @JsonValue
    public final int value;


    TaskStatus(int type) {
        this.value = type;
    }

    public boolean is(Number type) {
        return type != null && type.intValue() == this.value;
    }

    @JsonCreator
    public static TaskStatus parse(Number value) {
        for (TaskStatus v : values()) {
            if (v.is(value)) {
                return v;
            }
        }
        return UNKNOWN;
    }
}
