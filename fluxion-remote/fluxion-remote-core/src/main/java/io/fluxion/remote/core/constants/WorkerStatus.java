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
 * Worker的状态
 */
public enum WorkerStatus {

    UNKNOWN(CommonConstants.UNKNOWN),

    /**
     * 闲置
     */
    IDLE("idle"),
    /**
     * 初始化中
     */
    INITIALIZING("initializing"),
    /**
     * Worker正常运行中
     */
    RUNNING("running"),

    /**
     * Worker熔断中，此状态的Worker无法接受作业，并将等待心跳重连并复活。
     */
    FUSING("fusing"),

    /**
     * 关闭中
     */
    TERMINATING("terminating"),

    /**
     * Worker已停止。
     */
    TERMINATED("terminated"),

    ;

    @JsonValue
    public final String status;

    WorkerStatus(String status) {
        this.status = status;
    }

    public boolean is(String status) {
        return this.status.equals(status);
    }

    /**
     * 解析worker状态
     */
    @JsonCreator
    public static WorkerStatus parse(String status) {
        for (WorkerStatus statusEnum : values()) {
            if (statusEnum.is(status)) {
                return statusEnum;
            }
        }
        return UNKNOWN;
    }

    public boolean isRunning() {
        return is(RUNNING.status);
    }

}
