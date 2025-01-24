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

package io.fluxion.server.core.execution;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.fluxion.common.constants.CommonConstants;


/**
 * @author Devil
 */
public enum ExecutionStatus {
    UNKNOWN(CommonConstants.UNKNOWN),
    /**
     * 已经创建 等待下发
     */
    CREATED(1),
    /**
     * 队列中
     * 已下发给worker
     */
    QUEUED(2),
    /**
     * 运行中
     */
    RUNNING(3),
    SUCCEED(4),
    FAILED(5), // worker拒绝，进入容错策略 失败次数不增加 TERMINATED 作业被手动终止 不再增加一个状态 而是写入 errMsg
    /**
     * 重试 调度中
     */
    RESTARTED(6),
    /**
     * 终止
     * 比如重试时发现是未启用状态
     */
    CANCELLED(7),
    /**
     * 暂停
     */
    PAUSED(8),
    /**
     * 手工关闭任务
     */
    KILLING(9);

    @JsonValue
    public final int value;


    ExecutionStatus(int type) {
        this.value = type;
    }

    public boolean is(Number type) {
        return type != null && type.intValue() == this.value;
    }

    @JsonCreator
    public static ExecutionStatus parse(Number value) {
        for (ExecutionStatus v : values()) {
            if (v.is(value)) {
                return v;
            }
        }
        return UNKNOWN;
    }

    public boolean isFinished() {
        return this == FAILED || this == SUCCEED || this == CANCELLED;
    }

    public boolean isCreated() {
        return this == CREATED || this == RESTARTED;
    }

    public boolean isRunning() {
        return this == RUNNING || this == KILLING;
    }

    public boolean isFailed() {
        return this == FAILED;
    }

    public boolean isPaused() {
        return this == PAUSED;
    }
}
