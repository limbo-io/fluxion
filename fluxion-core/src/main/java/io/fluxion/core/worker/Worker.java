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

package io.fluxion.core.worker;

import io.fluxion.core.tag.Tagged;
import io.fluxion.core.worker.executor.WorkerExecutor;
import io.fluxion.core.worker.metric.WorkerMetric;
import io.fluxion.remote.lb.LBServer;
import lombok.Getter;

import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * @author Devil
 */
@Getter
public class Worker implements LBServer, Tagged {

    /**
     * 执行器
     */
    private List<WorkerExecutor> executors;

    /**
     * 标签
     */
    private Map<String, List<String>> tags;

    /**
     * Worker 状态指标
     */
    private WorkerMetric metric;

    /**
     * 是否启用 不启用则无法下发任务
     */
    private boolean enabled;

    @Override
    public String serverId() {
        return null;
    }

    @Override
    public boolean isAlive() {
        return false;
    }

    @Override
    public URL url() {
        return null;
    }

    @Override
    public Map<String, List<String>> tags() {
        return tags;
    }
}
