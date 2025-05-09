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

package io.fluxion.remote.core.lb;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.fluxion.common.constants.CommonConstants;

/**
 * 负载方式.
 * <ul>
 *     <li>{@linkplain LoadBalanceType#RANDOM 随机}</li>
 *     <li>{@linkplain LoadBalanceType#ROUND_ROBIN 轮询}</li>
 *     <li>{@linkplain LoadBalanceType#APPOINT 指定节点}</li>
 *     <li>{@linkplain LoadBalanceType#LEAST_FREQUENTLY_USED 最不经常使用}</li>
 *     <li>{@linkplain LoadBalanceType#LEAST_RECENTLY_USED 最近最少使用}</li>
 *     <li>{@linkplain LoadBalanceType#CONSISTENT_HASH 一致性hash}</li>
 * </ul>
 *
 * @author Brozen
 * @since 2021-05-16
 */
public enum LoadBalanceType {

    UNKNOWN(CommonConstants.UNKNOWN),
    /**
     * 随机。将作业随机下发给某一个worker执行（默认）
     */
    RANDOM("random"),

    /**
     * 轮询。
     */
    ROUND_ROBIN("round_robin"),

    /**
     * 指定节点，让作业指定下发到某个worker执行。
     */
    APPOINT("appoint"),

    /**
     * 最不经常使用。将作业下发给一个时间窗口内，接收作业最少的worker。
     */
    LEAST_FREQUENTLY_USED("least_frequently_used"),

    /**
     * 最近最少使用。将作业下发给一个时间窗口内，最长时间没有接受worker的worker。
     */
    LEAST_RECENTLY_USED("least_recently_used"),

    /**
     * 一致性hash。同样参数的作业将始终下发给同一台机器。
     */
    CONSISTENT_HASH("consistent_hash"),

    ;

    @JsonValue
    public final String value;

    LoadBalanceType(String value) {
        this.value = value;
    }

    public boolean is(String value) {
        return this.value.equals(value);
    }

    /**
     * 解析作业分发类型。
     */
    @JsonCreator
    public static LoadBalanceType parse(String value) {
        for (LoadBalanceType v : values()) {
            if (v.is(value)) {
                return v;
            }
        }
        return UNKNOWN;
    }

}
