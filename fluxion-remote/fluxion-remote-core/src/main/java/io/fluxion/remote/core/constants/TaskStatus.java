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

package io.fluxion.remote.core.constants;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.Sets;
import io.fluxion.common.constants.CommonConstants;

import java.util.Set;

/**
 * @author Devil
 */
public enum TaskStatus {
    UNKNOWN(CommonConstants.UNKNOWN),
    /**
     * 初始化
     */
    INITED("inited"),
    /**
     * 运行中
     */
    RUNNING("running"),
    SUCCEED("succeed"),
    FAILED("failed"), // worker拒绝，进入容错策略 失败次数不增加 TERMINATED 作业被手动终止 不再增加一个状态 而是写入 errMsg
    /**
     * 取消
     */
    CANCELLED("cancelled"),
    /**
     * 暂停
     */
    PAUSED("paused"),
    ;

    public static final Set<TaskStatus> FINISH_STATUS = Sets.newHashSet(
        SUCCEED, FAILED, CANCELLED
    );

    @JsonValue
    public final String value;


    TaskStatus(String type) {
        this.value = type;
    }

    public boolean is(String type) {
        return this.value.equals(type);
    }

    @JsonCreator
    public static TaskStatus parse(String value) {
        for (TaskStatus v : values()) {
            if (v.is(value)) {
                return v;
            }
        }
        return UNKNOWN;
    }

    public boolean isFinished() {
        return FINISH_STATUS.contains(this);
    }
}
