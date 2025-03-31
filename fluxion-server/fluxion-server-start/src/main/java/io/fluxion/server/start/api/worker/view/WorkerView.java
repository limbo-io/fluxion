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

package io.fluxion.server.start.api.worker.view;

import io.fluxion.remote.core.constants.Protocol;
import io.fluxion.server.core.worker.Worker;
import io.fluxion.server.core.worker.executor.WorkerExecutor;
import io.fluxion.server.core.worker.metric.WorkerMetric;
import io.fluxion.server.infrastructure.tag.Tag;
import lombok.Data;

import java.util.List;

/**
 * @author Devil
 */
@Data
public class WorkerView {

    private String appId;

    private Protocol protocol;

    private String host;

    private int port;

    /**
     * 执行器
     */
    private List<WorkerExecutor> executors;

    /**
     * 标签
     */
    private List<Tag> tags;

    /**
     * Worker 状态指标
     */
    private WorkerMetric metric;

    private Worker.Status status;

}
