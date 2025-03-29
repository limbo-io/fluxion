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
import io.fluxion.common.thread.NamedThreadFactory;
import io.fluxion.remote.core.client.Client;
import io.fluxion.remote.core.client.ClientFactory;
import io.fluxion.remote.core.client.server.ClientServer;
import io.fluxion.remote.core.constants.Protocol;
import io.fluxion.server.core.broker.task.BucketChecker;
import io.fluxion.server.core.broker.task.CoreTask;
import io.fluxion.server.core.broker.task.DataCleaner;
import io.fluxion.server.core.broker.task.ScheduleDelayLoader;
import io.fluxion.server.core.broker.task.ScheduleLoader;
import io.fluxion.server.infrastructure.schedule.schedule.DelayedTaskScheduler;
import io.fluxion.server.infrastructure.schedule.schedule.TimingWheelTimer;
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

    private final BrokerNode node;

    private final BrokerManger brokerManger;

    private final Client client;

    /**
     * Client服务
     */
    private final ClientServer clientServer;

    private final ScheduledExecutorService coreThreadPool;

    private final List<CoreTask> coreTasks;

    private final DelayedTaskScheduler delayedTaskScheduler;

    public Broker(Protocol protocol, String host, int port, BrokerManger brokerManger,
                  ClientServer clientServer) {
        Assert.isTrue(Protocol.UNKNOWN != protocol, "protocol is unknown");
        Assert.isTrue(StringUtils.isNotBlank(host), "host is null");

        this.node = new BrokerNode(protocol, host, port, 0);
        this.brokerManger = brokerManger;
        this.client = ClientFactory.create(protocol);
        this.coreTasks = Lists.newArrayList(
            new ScheduleLoader(),
            new ScheduleDelayLoader(),
            new BucketChecker(),
            new DataCleaner()
        );
        this.clientServer = clientServer;
        this.coreThreadPool = new ScheduledThreadPoolExecutor(
            coreTasks.size(),
            NamedThreadFactory.newInstance("FluxionBrokerCoreExecutor")
        );
        this.delayedTaskScheduler = new DelayedTaskScheduler(new TimingWheelTimer(100L, TimeUnit.MILLISECONDS));
    }

    /**
     * 启动节点
     */
    public void start() {
        // 初始化上下文
        BrokerContext.initialize(this);
        // 节点管理
        brokerManger.start();
        // 启动服务处理请求
        clientServer.start();
        // 启动核心任务
        for (CoreTask coreTask : coreTasks) {
            switch (coreTask.scheduleType()) {
                case FIXED_DELAY:
                    coreThreadPool.scheduleWithFixedDelay(coreTask, coreTask.getDelay(), coreTask.getInterval(), coreTask.getUnit());
                    break;
                case FIXED_RATE:
                    coreThreadPool.scheduleAtFixedRate(coreTask, coreTask.getDelay(), coreTask.getInterval(), coreTask.getUnit());
                    break;
            }
        }

        log.info("FluxionBroker start!!!~~~");
    }

    /**
     * 停止
     */
    public void stop() {
        brokerManger.stop();
        coreThreadPool.shutdown();
        clientServer.stop();
    }

    public String id() {
        return node.id();
    }

    public Client client() {
        return client;
    }

    public DelayedTaskScheduler delayedTaskScheduler() {
        return delayedTaskScheduler;
    }

    public BrokerNode node() {
        return node;
    }

}
