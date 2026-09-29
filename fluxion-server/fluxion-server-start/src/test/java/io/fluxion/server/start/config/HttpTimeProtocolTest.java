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

package io.fluxion.server.start.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 管理 HTTP API 的时间线协议回归（CONTEXT.md「统一时间模型」）：
 * 运行态 Instant 以 RFC 3339 UTC 字符串返回（WRITE_DATES_AS_TIMESTAMPS=false）。
 * <p>
 * 背景：Jackson 默认把 Instant 写成十进制秒；管理 API 的协议由本 mapper 配置锁定。
 * Worker RPC 不走本 mapper（其线协议为 epoch millis，DTO 字段为 Long，见 fluxion-test 的 TimeWireProtocolTest）。
 */
class HttpTimeProtocolTest {

    @Test
    @DisplayName("H1: Instant ↔ RFC 3339 UTC 字符串往返无损")
    void instantSerializesAsRfc3339UtcString() throws Exception {
        ObjectMapper mapper = new WebConfiguration().jacksonObjectMapper();

        Instant deadline = Instant.parse("2026-01-01T00:00:00Z");
        String json = mapper.writeValueAsString(deadline);

        // RFC 3339 UTC（无偏移、Z 后缀）；不是十进制秒，也不是 millis 整数
        assertThat(json).isEqualTo("\"2026-01-01T00:00:00Z\"");
        assertThat(mapper.readValue(json, Instant.class)).isEqualTo(deadline);
    }
}