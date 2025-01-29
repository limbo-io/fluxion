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

import io.fluxion.common.utils.MD5Utils;
import io.fluxion.remote.core.client.Client;
import io.fluxion.remote.core.constants.Protocol;
import io.fluxion.remote.core.lb.LBServer;
import io.fluxion.server.core.tag.Tagged;
import io.fluxion.server.core.task.Task;
import io.fluxion.server.core.worker.executor.WorkerExecutor;
import io.fluxion.server.core.worker.metric.WorkerMetric;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Devil
 */
@Getter
@Setter
public class Worker implements LBServer, Tagged {

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
    private Map<String, Set<String>> tags;

    /**
     * Worker 状态指标
     */
    private WorkerMetric metric;

    /**
     * 通信
     */
    private Client client;

    private WorkerStatus status;

    /**
     * 是否启用 不启用则无法下发任务
     */
    private boolean enabled;

    public String id() {
        // todo @d 优化性能，不需要每次都重新生成
        return MD5Utils.md5(appId + ":" + host + ":" + port);
    }

    @Override
    public String serverId() {
        return id();
    }

    @Override
    public boolean isAlive() {
        return WorkerStatus.RUNNING == status;
    }

    @Override
    public String host() {
        return host;
    }

    @Override
    public int port() {
        return port;
    }

    @Override
    public Map<String, Set<String>> tags() {
        return tags;
    }

    /**
     * 发送一个作业到worker执行。当worker接受此task后，将触发返回
     *
     * @param task 任务
     */
    public void dispatch(Task task) {

    }

}
