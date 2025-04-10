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

package io.fluxion.server.infrastructure.schedule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.fluxion.common.constants.CommonConstants;

/**
 * 调度方式：
 * <ul>
 *     <li>{@linkplain ScheduleType#FIXED_RATE 固定速度}</li>
 *     <li>{@linkplain ScheduleType#FIXED_DELAY 固定延迟}</li>
 *     <li>{@linkplain ScheduleType#CRON CRON}</li>
 * </ul>
 *
 * @author Brozen
 */
public enum ScheduleType {

    /**
     * unknown 不应该出现
     */
    UNKNOWN(CommonConstants.UNKNOWN),

    /**
     * 固定速度，作业创建后，每次调度下发后，间隔固定时间长度后，再次触发作业调度。
     */
    FIXED_RATE("fixed_rate"),

    /**
     * 固定延迟，作业创建后，每次作业下发执行完成（成功或失败）后，间隔固定时间长度后，再次触发作业调度。
     */
    FIXED_DELAY("fixed_delay"),

    /**
     * CRON表达式，通过CRON表达式指定作业触发调度的时间点。FIXED_RATE 的另一种模式
     */
    CRON("cron"),

    ;

    @JsonValue
    public final String value;


    ScheduleType(String value) {
        this.value = value;
    }

    public boolean is(String value) {
        return this.value.equals(value);
    }

    /**
     * 解析作业调度类型，用于Jackson反序列化
     *
     * @param value 数值类型的作业调度类型
     * @return 作业调度类型枚举
     */
    @JsonCreator
    public static ScheduleType parse(String value) {
        for (ScheduleType v : values()) {
            if (v.is(value)) {
                return v;
            }
        }
        return UNKNOWN;
    }

}
