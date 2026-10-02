/*
 * Copyright 2025-2030 fluxion-io Team (https://github.com/Fluxion-io).
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

package io.fluxion.server.start.api.observation.view;

import lombok.Data;

import java.util.Map;

/**
 * 存量观测快照，字段口径见 docs/guides/operations.md「可观测性现状」。
 *
 * @author Devil
 */
@Data
public class ObservationOverviewView {

    /**
     * Execution 状态 -> 数量（key 见 CONTEXT.md「Execution 状态」）
     */
    private Map<String, Long> executions;

    /**
     * 积压：status=pending 且 trigger_at <= now
     */
    private long executionBacklog;

    /**
     * 错过触发候选：status=pending 且 now - trigger_at > misfireThreshold(5s)
     */
    private long executionMisfireCandidates;

    /**
     * 可回收租约：status=claimed 且 lease_until <= now
     */
    private long executionReclaimableClaims;

    /**
     * 遗漏触发恢复：本 broker 自启动以来已恢复（含幂等确认）的遗漏点数；不携带 Schedule 标签
     */
    private long executionRecoveredPoints;

    /**
     * 遗漏触发恢复：本 broker 自启动以来明确丢弃（SKIP/激活 Deadline 已过）的遗漏点数
     */
    private long executionRecoveryDroppedPoints;

    /**
     * Job 状态 -> 数量（key 见 CONTEXT.md「Job 状态」）
     */
    private Map<String, Long> jobs;

    /**
     * Worker 状态 -> 数量（online/offline）
     */
    private Map<String, Long> workers;
}