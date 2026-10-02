/*
 * Copyright 2025-2030 limbo-io Team (https://github.com/limbo-io).
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

package io.fluxion.worker.core.job;

import java.time.Instant;

/**
 * @author Devil
 */
public class JobContext {

    private String id;

    /**
     * Job 的原始计划触发时刻（见 CONTEXT.md「Job 时间语义」）：调度 Execution 的首批
     * Job 继承 Execution.triggerAt；后续 Workflow 节点是自身就绪时刻。实际接收时刻由
     * Worker 本机在需要时自行取得（如 Instant.now()），不由 Broker 提供。
     */
    private Instant triggerAt;

    private JobContext() {}

    public String getId() {
        return id;
    }

    public Instant getTriggerAt() {
        return triggerAt;
    }

    public static JobContext from(Job job) {
        JobContext context = new JobContext();
        context.id = job.getId();
        context.triggerAt = job.getTriggerAt();
        return context;
    }
}