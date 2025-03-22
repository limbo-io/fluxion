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

package io.fluxion.server.core.worker;

import io.fluxion.remote.core.client.Client;
import io.fluxion.remote.core.constants.Protocol;
import io.fluxion.remote.core.constants.WorkerStatus;
import io.fluxion.remote.core.lb.LBServer;
import io.fluxion.server.core.app.App;
import io.fluxion.server.core.worker.executor.WorkerExecutor;
import io.fluxion.server.core.worker.metric.WorkerMetric;
import io.fluxion.server.infrastructure.tag.Tagged;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Devil
 */
@Getter
public class Worker implements LBServer, Tagged {

    private App app;

    private Protocol protocol;

    private String host;

    private int port;

    private String address;

    /**
     * 执行器
     */
    @Setter
    private List<WorkerExecutor> executors;

    /**
     * 标签
     */
    @Setter
    private Map<String, Set<String>> tags;

    /**
     * Worker 状态指标
     */
    @Setter
    private WorkerMetric metric;

    /**
     * 通信
     */
    @Setter
    private Client client;

    @Setter
    private WorkerStatus status;

    /**
     * 是否启用 不启用则无法下发任务
     */
    private boolean enabled = true;

    public Worker(App app, String host, int port, Protocol protocol) {
        this.app = app;
        this.host = host;
        this.port = port;
        this.protocol = protocol;
        this.address = host + ":" + port;
    }

    public String id() {
        return address;
    }

    @Override
    public Map<String, Set<String>> tags() {
        return tags;
    }

    @Override
    public boolean isAlive() {
        return WorkerStatus.RUNNING == status;
    }

    @Override
    public Protocol protocol() {
        return protocol;
    }

    @Override
    public String host() {
        return host;
    }

    @Override
    public int port() {
        return port;
    }
}
