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

package io.fluxion.server.core.worker.metric;

import lombok.*;

import java.time.LocalDateTime;

/**
 * worker的指标信息。
 *
 * @author Brozen
 * @since 2021-05-17
 */
@Data
@Setter(AccessLevel.NONE)
@AllArgsConstructor
@Builder(builderClassName = "Builder", toBuilder = true)
public class WorkerMetric {

    /**
     * cpu 核心数
     */
    private int cpuProcessors;

    private double cpuLoad;

    private long freeMemory;

    /**
     * 任务队列剩余可排队数
     */
    private int availableQueueNum;

    /**
     * 上次心跳上报时间戳，毫秒
     */
    private long lastHeartbeatAt;

}
