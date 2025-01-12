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

package io.fluxion.server.infrastructure.schedule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.fluxion.common.utils.time.TimeUtils;
import lombok.Getter;

import javax.validation.constraints.NotNull;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 作业调度配置，值对象。
 *
 * @author Brozen
 * @since 2021-06-01
 */
@Getter
public class ScheduleOption {

    /**
     * 调度方式
     */
    @NotNull
    private final ScheduleType scheduleType;

    /**
     * 调度开始时间，从此时间开始执行调度。
     */
    @NotNull
    private final LocalDateTime scheduleStartAt;

    /**
     * 调度结束时间，从此时间结束执行调度。
     */
    private final LocalDateTime scheduleEndAt;

    /**
     * 延迟时间 -- 当前时间多久后调度
     */
    private final Duration scheduleDelay;

    /**
     * 获取调度间隔时间。
     * 当调度方式为{@link ScheduleType#FIXED_DELAY}时，表示前一次作业调度执行完成后，隔多久触发第二次调度。
     * 当调度方式为{@link ScheduleType#FIXED_RATE}时，表示前一次作业调度下发后，隔多久触发第二次调度。
     */
    private final Duration scheduleInterval;

    /**
     * 作业调度的CRON表达式
     * 当调度方式为{@link ScheduleType#CRON}时，根据此CRON表达式计算得到的时间点触发作业调度。
     */
    private final String scheduleCron;

    /**
     * 作业调度的CRON表达式的类型
     * 当调度方式为{@link ScheduleType#CRON}时，根据此CRON表达式类型计算得到的时间点触发作业调度。{@link com.cronutils.model.CronType}
     */
    private final String scheduleCronType;

    @JsonCreator
    public ScheduleOption(@JsonProperty("scheduleType") ScheduleType scheduleType,
                          @JsonProperty("scheduleStartAt") LocalDateTime scheduleStartAt,
                          @JsonProperty("scheduleEndAt") LocalDateTime scheduleEndAt,
                          @JsonProperty("scheduleDelay") Duration scheduleDelay,
                          @JsonProperty("scheduleInterval") Duration scheduleInterval,
                          @JsonProperty("scheduleCron") String scheduleCron,
                          @JsonProperty("scheduleCronType") String scheduleCronType) {
        this.scheduleType = scheduleType;
        this.scheduleStartAt = scheduleStartAt == null ? TimeUtils.currentLocalDateTime() : scheduleStartAt;
        this.scheduleEndAt = scheduleEndAt;
        this.scheduleDelay = scheduleDelay == null ? Duration.ZERO : scheduleDelay;
        this.scheduleInterval = scheduleInterval == null ? Duration.ZERO : scheduleInterval;
        this.scheduleCron = scheduleCron;
        this.scheduleCronType = scheduleCronType;
    }

}