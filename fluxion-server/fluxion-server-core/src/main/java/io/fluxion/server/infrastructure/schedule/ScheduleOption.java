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
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import javax.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static io.limbo.utils.time.TimeUtils.TIME_FOREVER;

/**
 * 作业调度配置，值对象。
 * <p>
 * <b>Misfire Policy (LATEST_ONLY):</b>
 * <ul>
 *     <li><b>CRON/FIXED_RATE:</b> When broker recovers with multiple missed trigger points, 
 *         only the latest valid trigger time is executed. Stale triggers are skipped.</li>
 *     <li><b>FIXED_DELAY:</b> Continues from the last completion time. Does not create 
 *         concurrent historical instances - only schedules the next single trigger point.</li>
 * </ul>
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
    private final ScheduleType type;

    /**
     * 调度开始时间，从此时间开始执行调度。
     */
    @NotNull
    private final LocalDateTime startTime;

    /**
     * 调度结束时间，从此时间结束执行调度。
     */
    private final LocalDateTime endTime;

    /**
     * 解释 Cron 与日历时间字段的 IANA 时区。
     */
    @NotNull
    private final ZoneId timeZone;

    /**
     * 延迟时间 -- 当前时间多久后调度
     */
    private final Duration delay;

    /**
     * 获取调度间隔时间。
     * 当调度方式为{@link ScheduleType#FIXED_DELAY}时，表示前一次作业调度执行完成后，隔多久触发第二次调度。
     * 当调度方式为{@link ScheduleType#FIXED_RATE}时，表示前一次作业调度下发后，隔多久触发第二次调度。
     */
    private final Duration interval;

    /**
     * 作业调度的CRON表达式
     * 当调度方式为{@link ScheduleType#CRON}时，根据此CRON表达式计算得到的时间点触发作业调度。
     */
    private final String cron;

    /**
     * 作业调度的CRON表达式的类型
     * 当调度方式为{@link ScheduleType#CRON}时，根据此CRON表达式类型计算得到的时间点触发作业调度。{@link com.cronutils.model.CronType}
     */
    private final String cronType;

    /** Misfire handling for scheduled Executions. */
    private final MisfirePolicy misfirePolicy;

    /** Total allowed Job-creation attempts after a misfire. */
    private final int maxFireAttempts;

    /**
     * 首个 Job 允许启动的最长延后时间（毫秒）；null 表示不限制。
     */
    private final Long maxStartDelayMillis;

    @JsonCreator
    public ScheduleOption(@JsonProperty("type") ScheduleType type,
                          @JsonProperty("startTime") LocalDateTime startTime,
                          @JsonProperty("endTime") LocalDateTime endTime,
                          @JsonProperty("scheduleDelay") Duration delay,
                          @JsonProperty("scheduleInterval") Duration interval,
                          @JsonProperty("cron") String cron,
                          @JsonProperty("cronType") String cronType,
                          @JsonProperty("misfirePolicy") MisfirePolicy misfirePolicy,
                          @JsonProperty("maxFireAttempts") Integer maxFireAttempts,
                          @JsonProperty("timeZone") String timeZone,
                          @JsonProperty("maxStartDelayMillis") Long maxStartDelayMillis) {
        this.type = type;
        if (timeZone == null || timeZone.trim().isEmpty()) {
            throw new IllegalArgumentException("timeZone is required");
        }
        this.timeZone = ZoneId.of(timeZone);
        this.startTime = startTime == null ? LocalDateTime.now(this.timeZone) : startTime;
        this.endTime = endTime == null ? TIME_FOREVER : endTime;
        this.delay = delay == null ? Duration.ZERO : delay;
        this.interval = interval == null ? Duration.ZERO : interval;
        this.cron = cron;
        this.cronType = cronType;
        this.misfirePolicy = misfirePolicy == null ? MisfirePolicy.FIRE_RETRY : misfirePolicy;
        this.maxFireAttempts = maxFireAttempts == null ? 1 : Math.max(1, maxFireAttempts);
        if (maxStartDelayMillis != null && maxStartDelayMillis < 1000L) {
            throw new IllegalArgumentException("maxStartDelayMillis must be at least 1000");
        }
        this.maxStartDelayMillis = maxStartDelayMillis;
    }

    public ScheduleOption(ScheduleType type, LocalDateTime startTime, LocalDateTime endTime,
                          Duration delay, Duration interval, String cron, String cronType, ZoneId timeZone) {
        this(type, startTime, endTime, delay, interval, cron, cronType, null, null,
            timeZone == null ? null : timeZone.getId(), null);
    }

    /**
     * 生效窗口起点的绝对时刻：以配置时区解释。供运行态比较使用
     * （见 CONTEXT.md「统一时间模型」：日历配置在边界处解释为绝对时间）。
     */
    public Instant startInstant() {
        return startTime == null ? null : startTime.atZone(timeZone).toInstant();
    }

    /**
     * 生效窗口终点的绝对时刻：以配置时区解释。
     */
    public Instant endInstant() {
        return endTime == null ? null : endTime.atZone(timeZone).toInstant();
    }

}
