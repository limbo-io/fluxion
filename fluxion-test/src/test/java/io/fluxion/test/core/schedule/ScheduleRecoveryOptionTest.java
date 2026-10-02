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

package io.fluxion.test.core.schedule;

import io.fluxion.server.core.schedule.converter.ScheduleEntityConverter;
import io.fluxion.server.infrastructure.dao.entity.ScheduleEntity;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import io.limbo.utils.json.JacksonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 遗漏触发恢复配置边界（见 CONTEXT.md「遗漏触发恢复」）：
 * 默认关闭、可显式开启、FIXED_DELAY 拒绝开启、持久化双向映射不丢失。
 */
@DisplayName("Schedule recoveryEnabled config & persistence")
class ScheduleRecoveryOptionTest {

    private static final ZoneId TZ = ZoneId.of("UTC");

    @Test
    @DisplayName("默认关闭：未配置 recoveryEnabled 时为 false")
    void recoveryDisabledByDefault() {
        ScheduleOption option = new ScheduleOption(
            ScheduleType.FIXED_RATE, null, null, Duration.ZERO, Duration.ofMinutes(1),
            null, null, null, null, "UTC", null, null);

        assertThat(option.isRecoveryEnabled()).isFalse();
    }

    @Test
    @DisplayName("CRON/FIXED_RATE 可显式开启恢复")
    void recoveryCanBeEnabledForCalendarTypes() {
        ScheduleOption cron = new ScheduleOption(
            ScheduleType.CRON, null, null, Duration.ZERO, Duration.ZERO,
            "0 * * * * ?", null, null, null, "UTC", null, true);
        ScheduleOption fixedRate = new ScheduleOption(
            ScheduleType.FIXED_RATE, null, null, Duration.ZERO, Duration.ofMinutes(1),
            null, null, null, null, "UTC", null, true);

        assertThat(cron.isRecoveryEnabled()).isTrue();
        assertThat(fixedRate.isRecoveryEnabled()).isTrue();
    }

    @Test
    @DisplayName("FIXED_DELAY 依赖反馈推进，配置边界直接拒绝开启恢复")
    void fixedDelayRejectsRecovery() {
        assertThrows(IllegalArgumentException.class, () -> new ScheduleOption(
            ScheduleType.FIXED_DELAY, null, null, Duration.ZERO, Duration.ofMinutes(1),
            null, null, null, null, "UTC", null, true));
        // 关闭时合法
        ScheduleOption closed = new ScheduleOption(
            ScheduleType.FIXED_DELAY, null, null, Duration.ZERO, Duration.ofMinutes(1),
            null, null, null, null, "UTC", null, null);
        assertThat(closed.isRecoveryEnabled()).isFalse();
    }

    @Test
    @DisplayName("JSON 反序列化沿用同一配置边界：缺失字段为默认关闭，显式 true 生效")
    void jsonDeserializationHonorsConfigBoundary() {
        // LocalDateTime 走 JacksonUtils 的 YMD_HMS 图案；类型用存储枚举值（小写）
        String withoutRecovery = "{\"type\":\"fixed_rate\",\"timeZone\":\"UTC\","
            + "\"scheduleInterval\":\"PT60S\",\"startTime\":\"2026-01-01 00:00:00\"}";
        ScheduleOption parsed = JacksonUtils.toType(withoutRecovery, ScheduleOption.class);
        assertThat(parsed.isRecoveryEnabled()).isFalse();

        String withRecovery = "{\"type\":\"fixed_rate\",\"timeZone\":\"UTC\","
            + "\"scheduleInterval\":\"PT60S\",\"startTime\":\"2026-01-01 00:00:00\",\"recoveryEnabled\":true}";
        ScheduleOption enabled = JacksonUtils.toType(withRecovery, ScheduleOption.class);
        assertThat(enabled.isRecoveryEnabled()).isTrue();

        String fixedDelayWithRecovery = "{\"type\":\"fixed_delay\",\"timeZone\":\"UTC\","
            + "\"scheduleInterval\":\"PT60S\",\"startTime\":\"2026-01-01 00:00:00\",\"recoveryEnabled\":true}";
        assertThrows(RuntimeException.class,
            () -> JacksonUtils.toType(fixedDelayWithRecovery, ScheduleOption.class));
    }

    @Test
    @DisplayName("持久化双向映射：assemble 写入 recovery_enabled，toOption 读回不丢失")
    void converterRoundTripsRecoveryFlag() {
        ScheduleOption option = new ScheduleOption(
            ScheduleType.FIXED_RATE, LocalDateTime.of(2026, 1, 1, 0, 0), null,
            Duration.ZERO, Duration.ofMinutes(1),
            null, null, null, null, "UTC", null, true);
        ScheduleEntity entity = new ScheduleEntity();
        entity.setScheduleId("schedule-recover-rt");
        ScheduleEntityConverter.assemble(entity, option);
        entity.setLastTriggerAt(null);
        entity.setNextTriggerAt(null);
        entity.setEnabled(true);

        assertThat(entity.isRecoveryEnabled()).isTrue();
        assertThat(ScheduleEntityConverter.toOption(entity).isRecoveryEnabled()).isTrue();

        // 关闭方向
        ScheduleOption off = new ScheduleOption(
            ScheduleType.FIXED_RATE, LocalDateTime.of(2026, 1, 1, 0, 0), null,
            Duration.ZERO, Duration.ofMinutes(1),
            null, null, null, null, "UTC", null, null);
        ScheduleEntity closedEntity = new ScheduleEntity();
        ScheduleEntityConverter.assemble(closedEntity, off);
        assertThat(closedEntity.isRecoveryEnabled()).isFalse();
    }
}