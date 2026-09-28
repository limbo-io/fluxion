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
import io.fluxion.common.constants.CommonConstants;

/**
 * Worker 对一次 Job 派发返回的确定性结果（见 CONTEXT.md「Job 派发结果」）。
 * <p>
 * {@link #ACCEPTED} 表示已接受并会开始处理；
 * {@link #REJECTED_DEADLINE} 表示激活截止时间已到，必须收敛为 EXPIRED 而不重试；
 * {@link #REJECTED_UNAVAILABLE} 表示 Worker 暂不可用，仍可按 Job 容错策略处理。
 *
 * @author Devil
 */
public enum JobDispatchResult {
    UNKNOWN(CommonConstants.UNKNOWN),
    /**
     * 已接受并会开始处理
     */
    ACCEPTED("accepted"),
    /**
     * 激活截止时间已到，必须收敛为 EXPIRED 而不重试
     */
    REJECTED_DEADLINE("rejected_deadline"),
    /**
     * Worker 暂不可用，仍可按 Job 容错策略处理
     */
    REJECTED_UNAVAILABLE("rejected_unavailable"),
    ;

    @JsonValue
    public final String value;

    JobDispatchResult(String type) {
        this.value = type;
    }

    public boolean is(String type) {
        return this.value.equals(type);
    }

    @JsonCreator
    public static JobDispatchResult parse(String value) {
        for (JobDispatchResult v : values()) {
            if (v.is(value)) {
                return v;
            }
        }
        return UNKNOWN;
    }
}