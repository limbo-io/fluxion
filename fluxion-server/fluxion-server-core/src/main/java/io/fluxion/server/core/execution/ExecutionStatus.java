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
     * 已按计划创建，尚未被 Broker 领取。
     */
    PENDING("pending"),
    /**
     * Broker 持有短租约，正在校验 Trigger 并创建 Job。
     */
    CLAIMED("claimed"),
    /**
     * 初始化
     */
    INITED("inited"),
    /**
     * 运行中
     */
    RUNNING("running"),
    /**
     * Trigger 未启用或已发布新版本，实例不再执行。
     */
    INVALID("invalid"),
    /**
     * 错过触发策略明确要求不执行。
     */
    SKIPPED("skipped"),
    /**
     * 错过触发补偿创建 Job 的次数已耗尽。
     */
    MISFIRED("misfired"),
    /**
     * 超过创建首批 Job 的最晚允许时间，不再执行：
     * 领取前/建 Job 前的 Broker 预检查，或首批 Job 下发时被 Worker/本地因截止时间拒绝。
     */
    EXPIRED("expired"),
    /**
     * Job 已创建，且 Execution 的全部完成条件已满足。
     */
    SUCCEED("succeed"),
    /**
     * Job 已创建，但执行或工作流的失败规则判定本次 Execution 失败。
     */
    FAILED("failed");

    @JsonValue
    public final String value;


    ExecutionStatus(String type) {
        this.value = type;
    }

    public boolean is(String type) {
        return this.value.equals(type);
    }

    @JsonCreator
    public static ExecutionStatus parse(String value) {
        for (ExecutionStatus v : values()) {
            if (v.is(value)) {
                return v;
            }
        }
        return UNKNOWN;
    }

    public boolean isFinished() {
        return this == FAILED || this == SUCCEED
            || this == SKIPPED || this == MISFIRED || this == EXPIRED || this == INVALID;
    }

    public boolean isCreated() {
        return this == INITED || this == PENDING || this == CLAIMED;
    }

    public boolean isRunning() {
        return this == RUNNING;
    }

    public boolean isFailed() {
        return this == FAILED;
    }
}
