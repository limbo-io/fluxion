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

package io.fluxion.test.core.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fluxion.remote.core.api.request.broker.JobStateTransitionRequest;
import io.limbo.utils.json.JacksonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 时间线协议回归（CONTEXT.md「统一时间模型」）：
 * <ul>
 *   <li>Worker RPC（JacksonUtils）：绝对时间以 UTC epoch millis 整数传输；
 *       由 DTO 的 Long 字段承载类型契约，不依赖共享 mapper 的时间戳行为。</li>
 *   <li>管理 HTTP API 的 RFC 3339 协议回归在 fluxion-server-start 的 HttpTimeProtocolTest。</li>
 * </ul>
 * 背景：Jackson 的 JavaTimeModule 默认把 Instant 序列化为十进制秒（非 millis），整数 millis 输入
 * 会被 InstantDeserializer 按秒解释。协议必须显式锁定，本测试防止任何回归。
 */
class TimeWireProtocolTest {

    private static final Instant DEADLINE = Instant.parse("2026-01-01T00:00:00Z");
    private static final long DEADLINE_MILLIS = 1767225600000L;

    @Test
    @DisplayName("W1: RPC 通道——DTO Long 字段以 epoch millis 整数过线并在对端无损还原")
    void rpcWireCarriesEpochMillisIntegers() {
        JobStateTransitionRequest request = new JobStateTransitionRequest();
        request.setReportAt(DEADLINE_MILLIS);
        request.setJobId("job-w1");

        String json = JacksonUtils.toJSONString(request);
        // 线上是毫秒整数——不是十进制秒，不是 ISO 字符串
        assertThat(json).contains("\"reportAt\":1767225600000");

        JobStateTransitionRequest parsed = JacksonUtils.toType(json, JobStateTransitionRequest.class);
        assertThat(parsed.getReportAt()).isEqualTo(DEADLINE_MILLIS);
        assertThat(Instant.ofEpochMilli(parsed.getReportAt())).isEqualTo(DEADLINE);
    }

    @Test
    @DisplayName("W3: 基线认知锁定——裸 Instant 在 JacksonUtils 通道不是 millis；RPC DTO 时间字段必须用 Long")
    void rawInstantIsNotMillisOnSharedRpcMapper() {
        String json = JacksonUtils.toJSONString(DEADLINE);

        // 与 millis 整数进行比较：无论 Jackson 版本输出十进制秒还是 ISO 字符串，都不是 epoch millis,
        // 且数字输入会被 InstantDeserializer 按秒解释(整数 millis → 公元 5 万年级错值).
        // 因此共亨 RPC mapper 上不允许出现裸 Instant 字段:协议只能由 DTO 的 Long 字段承载(W1).
        assertThat(json).isNotEqualTo("1767225600000");
    }
}