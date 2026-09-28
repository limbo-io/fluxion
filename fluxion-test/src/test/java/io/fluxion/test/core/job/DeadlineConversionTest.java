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

package io.fluxion.test.core.job;

import io.fluxion.server.core.job.runner.ExecutorJobRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deadline 墙钟 → RPC epoch 换算的确定性测试。
 * <p>
 * 固定 UTC 口径（审查修复 P1-1）：同一 DB 墙钟值在任意时区的节点上换算出同一绝对 epoch，
 * 不随 systemDefault 漂移。
 */
class DeadlineConversionTest {

    @Test
    @DisplayName("D1: 固定 UTC 口径换算，结果与机器时区无关")
    void shouldConvertWithUtcOffsetRegardlessOfHostTimeZone() {
        LocalDateTime deadline = LocalDateTime.of(2026, 1, 1, 0, 0, 0);

        // 2026-01-01T00:00:00Z 的绝对 epoch millis（固定期望值，任何机器上断言一致）
        assertThat(ExecutorJobRunner.toDeadlineEpochMillis(deadline)).isEqualTo(1767225600000L);
        assertThat(ExecutorJobRunner.toDeadlineEpochMillis(null)).isNull();
    }
}