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

package io.fluxion.server.core.broker;

import io.fluxion.common.constants.CommonConstants;
import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.remote.core.constants.Protocol;
import io.fluxion.server.core.cluster.Node;
import io.fluxion.server.core.cluster.NodeManger;
import io.fluxion.server.core.cluster.NodeRegistry;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.id.cmd.IDGenerateCmd;
import io.fluxion.server.infrastructure.id.data.IDType;
import io.fluxion.server.infrastructure.schedule.scheduler.ScheduledTaskScheduler;
import io.fluxion.server.infrastructure.schedule.scheduler.TaskScheduler;
import io.fluxion.server.infrastructure.schedule.scheduler.TimingWheelTimer;
import io.fluxion.server.infrastructure.schedule.task.ScheduledTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author Devil
 * @since 2022/7/20
 */
@Slf4j
public class Broker {

    private final String id;

    private final Node node;

    private final NodeRegistry registry;

    private final NodeManger manger;

    private final TaskScheduler<ScheduledTask> taskScheduler;

    public Broker(Map<Protocol, List<Node.Address>> protocols, NodeRegistry registry, NodeManger manger) {
        if (MapUtils.isEmpty(protocols)) {
            throw new IllegalArgumentException("protocols can't be empty");
        }

        String id = Cmd.send(new IDGenerateCmd(IDType.BROKER)).getId();
        this.id = id;
        this.node = new Node(id, protocols);
        this.registry = registry;
        this.manger = manger;
        this.taskScheduler = new ScheduledTaskScheduler(new TimingWheelTimer(100L, TimeUnit.MILLISECONDS));
    }

    /**
     * 启动节点
     */
    public void start() {
        // 将自己上线管理
        manger.online(node);
        // 节点注册 用于集群感知
        registry.register(node);
        // 节点变更通知
        registry.subscribe(event -> {
            switch (event.getType()) {
                case ONLINE:
                    manger.online(event.getNode());
                    if (log.isDebugEnabled()) {
                        log.debug("[BrokerNodeListener] receive online evnet {}", JacksonUtils.toJSONString(event));
                    }
                    break;
                case OFFLINE:
                    manger.offline(event.getNode());
                    if (log.isDebugEnabled()) {
                        log.debug("[BrokerNodeListener] receive offline evnet {}", JacksonUtils.toJSONString(event));
                    }
                    break;
                default:
                    log.warn("[BrokerNodeListener] " + CommonConstants.UNKNOWN + " evnet {}", JacksonUtils.toJSONString(event));
                    break;
            }
        });

        //

        log.info("FluxionBroker start!!!~~~");
    }

    /**
     * 停止
     */
    public void stop() {
        registry.stop();
    }

    public String id() {
        return id;
    }

}
