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

package io.fluxion.server.core.schedule;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 遗漏触发恢复的汇总观测（见 CONTEXT.md「遗漏触发恢复观测」）：只按结果累计
 * 已恢复与已丢弃数量，用于告警与容量判断。不携带 Schedule/Trigger 标签，不为
 * 丢弃点建审计表，也不逐点打日志。
 * <p>
 * 计数为 broker 本机累计（重启归零）；多机部署时分别对各 broker 的观测接口取值。
 * ponytail: 若需要跨重启持久或聚合到全局，再升级为 DB 计数行，当前告警阈值够用。
 */
@Component
public class ScheduleRecoveryMetrics {

    /** 已恢复：候选点已存在同点 Execution 的幂等确认，或本轮物化成功 */
    private final AtomicLong recovered = new AtomicLong();

    /** 已丢弃：候选点命中 SKIP/激活 Deadline 已过而被明确丢弃 */
    private final AtomicLong dropped = new AtomicLong();

    public void recordRecovered(int count) {
        if (count > 0) {
            recovered.addAndGet(count);
        }
    }

    public void recordDropped(int count) {
        if (count > 0) {
            dropped.addAndGet(count);
        }
    }

    public long getRecovered() {
        return recovered.get();
    }

    public long getDropped() {
        return dropped.get();
    }
}