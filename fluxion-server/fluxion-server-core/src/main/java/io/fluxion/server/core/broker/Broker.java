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

import com.google.common.collect.Lists;
import io.fluxion.common.constants.CommonConstants;
import io.fluxion.common.thread.NamedThreadFactory;
import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.remote.core.client.Client;
import io.fluxion.remote.core.client.ClientFactory;
import io.fluxion.remote.core.cluster.Node;
import io.fluxion.remote.core.constants.Protocol;
import io.fluxion.server.core.broker.task.CoreTask;
import io.fluxion.server.core.broker.task.ScheduleLoadTask;
import io.fluxion.server.core.cluster.NodeManger;
import io.fluxion.server.core.cluster.NodeRegistry;
import io.fluxion.server.infrastructure.schedule.schedule.DelayedTaskScheduler;
import io.fluxion.server.infrastructure.schedule.schedule.ScheduledTaskScheduler;
import io.fluxion.server.infrastructure.schedule.schedule.TimingWheelTimer;
import io.fluxion.server.infrastructure.schedule.scheduler.TaskScheduler;
import io.fluxion.server.infrastructure.schedule.task.ScheduledTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Devil
 * @since 2022/7/20
 */
@Slf4j
public class Broker {

    private final Node node;

    private final NodeRegistry registry;

    private final NodeManger manger;

    private final Client client;

    private final ScheduledExecutorService coreThreadPool;

    private final List<CoreTask> coreTasks;

    private final ScheduledTaskScheduler scheduledTaskScheduler;

    private final DelayedTaskScheduler delayedTaskScheduler;

    public Broker(Protocol protocol, String host, int port, NodeRegistry registry, NodeManger manger) {
        Assert.isTrue(Protocol.UNKNOWN != protocol, "protocol is unknown");
        Assert.isTrue(StringUtils.isNotBlank(host), "host is null");

        this.node = new Node(protocol, host, port);
        this.registry = registry;
        this.manger = manger;
        this.client = ClientFactory.create(protocol);
        this.coreTasks = Lists.newArrayList(
            new ScheduleLoadTask(1000)
        );
        this.coreThreadPool = new ScheduledThreadPoolExecutor(
            coreTasks.size(),
            NamedThreadFactory.newInstance("FluxionBrokerCoreExecutor")
        );
        this.scheduledTaskScheduler = new ScheduledTaskScheduler(new TimingWheelTimer(100L, TimeUnit.MILLISECONDS));
        this.delayedTaskScheduler = new DelayedTaskScheduler(new TimingWheelTimer(100L, TimeUnit.MILLISECONDS));
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

        // 启动核心任务
        for (CoreTask coreTask : coreTasks) {
            coreThreadPool.scheduleWithFixedDelay(coreTask, 0, coreTask.getInterval(), TimeUnit.MILLISECONDS);
        }

        BrokerContext.initialize(this);

        log.info("FluxionBroker start!!!~~~");
    }

    /**
     * 停止
     */
    public void stop() {
        registry.stop();
        coreThreadPool.shutdown();
    }

    public String id() {
        return node.serverId();
    }

    public Client client() {
        return client;
    }

    public ScheduledTaskScheduler scheduledTaskScheduler() {
        return scheduledTaskScheduler;
    }

    public DelayedTaskScheduler delayedTaskScheduler() {
        return delayedTaskScheduler;
    }

}
