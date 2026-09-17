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

package io.fluxion.server.core.observation;

import io.limbo.cqrs.core.queryhandling.IQuery;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

/**
 * 存量观测快照查询（详见 docs/guides/operations.md「可观测性现状」）。
 * <p>
 * 只回答库存量：状态分布、积压、错过触发候选、可回收租约与 Worker 在线分布；
 * 速率型指标（claim 冲突、lease 回收频次等）依赖结构化日志，不属于本查询范围。
 *
 * @author Devil
 */
@Getter
@AllArgsConstructor
public class ObservationOverviewQuery implements IQuery<ObservationOverviewQuery.Response> {

    @Getter
    @AllArgsConstructor
    public static class Response {

        /** Execution 状态 -> 数量（key 为 ExecutionStatus.value） */
        private Map<String, Long> executions;

        /** 积压：status=pending 且 trigger_at <= now 的 Execution 数 */
        private long executionBacklog;

        /** 错过触发候选：status=pending 且 trigger_at < now - misfireThreshold 的 Execution 数 */
        private long executionMisfireCandidates;

        /** 可回收租约：status=claimed 且 lease_until <= now 的 Execution 数 */
        private long executionReclaimableClaims;

        /** Job 状态 -> 数量（key 为 JobStatus.value） */
        private Map<String, Long> jobs;

        /** Worker 状态 -> 数量（key 为 Worker.Status.status：online/offline） */
        private Map<String, Long> workers;
    }
}