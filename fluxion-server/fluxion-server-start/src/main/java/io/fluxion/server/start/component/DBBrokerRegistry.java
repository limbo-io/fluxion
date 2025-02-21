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

package io.fluxion.server.start.component;

import io.fluxion.common.thread.NamedThreadFactory;
import io.fluxion.common.utils.MD5Utils;
import io.fluxion.common.utils.time.Formatters;
import io.fluxion.common.utils.time.LocalTimeUtils;
import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.remote.core.cluster.Node;
import io.fluxion.remote.core.constants.Protocol;
import io.fluxion.server.core.cluster.NodeEvent;
import io.fluxion.server.core.cluster.NodeListener;
import io.fluxion.server.core.cluster.NodeRegistry;
import io.fluxion.server.infrastructure.dao.entity.BrokerEntity;
import io.fluxion.server.infrastructure.dao.repository.BrokerEntityRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 基于db的发布订阅
 *
 * @author Devil
 * @since 2022/7/15
 */
@Slf4j
@Component
public class DBBrokerRegistry implements NodeRegistry {

    private final BrokerEntityRepo brokerEntityRepo;

    /**
     * 心跳时间间隔，毫秒
     */
    private final Duration heartbeatInterval;

    /**
     * 心跳超时时间，毫秒
     */
    private final Duration heartbeatTimeout;

    private final List<NodeListener> listeners;

    private final ScheduledExecutorService scheduledExecutorService;

    public DBBrokerRegistry(BrokerEntityRepo brokerEntityRepo) {
        this.heartbeatInterval = Duration.ofMillis(2000);
        this.heartbeatTimeout = Duration.ofMillis(5000);
        this.brokerEntityRepo = brokerEntityRepo;
        this.listeners = new ArrayList<>();
        this.scheduledExecutorService = new ScheduledThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 4,
            NamedThreadFactory.newInstance("FluxionBrokerRegistry")
        );
    }

    @Override
    public void register(Node node) {
        // 新建
        BrokerEntity broker = entity(node);
        broker.setLastHeartbeat(TimeUtils.currentLocalDateTime());
        brokerEntityRepo.saveAndFlush(broker);

        // 开启定时任务 维持心跳
        scheduledExecutorService.scheduleAtFixedRate(
            new HeartbeatTask(node.serverId(), node), 0, heartbeatInterval.toMillis(), TimeUnit.MILLISECONDS
        );

        // 开启定时任务，监听broker心跳情况
        scheduledExecutorService.scheduleAtFixedRate(
            new NodeOnlineCheckTask(), 0, heartbeatTimeout.toMillis(), TimeUnit.MILLISECONDS
        );
        scheduledExecutorService.scheduleAtFixedRate(
            new NodeOfflineCheckTask(), 0, heartbeatTimeout.toMillis(), TimeUnit.MILLISECONDS
        );
    }

    @Override
    public void subscribe(NodeListener listener) {
        if (listener == null) {
            return;
        }
        listeners.add(listener);
    }

    @Override
    public void stop() {
        scheduledExecutorService.shutdown();
    }

    private Node node(BrokerEntity entity) {
        return new Node(Protocol.parse(entity.getProtocol()), entity.getHost(), entity.getPort());
    }

    private BrokerEntity entity(Node node) {
        BrokerEntity entity = new BrokerEntity();
        entity.setBrokerId(MD5Utils.md5(node.serverId()));
        entity.setProtocol(node.protocol().getValue());
        entity.setHost(node.host());
        entity.setPort(node.port());
        return entity;
    }

    private class HeartbeatTask extends TimerTask {
        private static final String TASK_NAME = "[HeartbeatTask]";
        private final String id;
        private final Node node;

        public HeartbeatTask(String id, Node node) {
            this.id = id;
            this.node = node;
        }

        @Override
        public void run() {
            try {
                LocalDateTime now = TimeUtils.currentLocalDateTime();
                BrokerEntity broker = entity(node);
                broker.setLastHeartbeat(now);
                brokerEntityRepo.saveAndFlush(broker);
                if (log.isDebugEnabled()) {
                    log.debug("{} send heartbeat id: {} time:{}", TASK_NAME, id, LocalTimeUtils.format(now, Formatters.YMD_HMS));
                }
            } catch (Exception e) {
                log.error("{} send heartbeat fail", TASK_NAME, e);
            }
        }

    }

    private class NodeOnlineCheckTask extends TimerTask {

        private static final String TASK_NAME = "[NodeOnlineCheckTask]";

        LocalDateTime lastCheckTime = TimeUtils.currentLocalDateTime().plusSeconds(-heartbeatTimeout.getSeconds());

        @Override
        public void run() {
            try {
                LocalDateTime startTime = lastCheckTime;
                LocalDateTime endTime = TimeUtils.currentLocalDateTime();
                if (log.isDebugEnabled()) {
                    log.info("{} checkOnline start:{} end:{}", TASK_NAME, LocalTimeUtils.format(startTime, Formatters.YMD_HMS), LocalTimeUtils.format(endTime, Formatters.YMD_HMS));
                }
                List<BrokerEntity> onlineBrokers = brokerEntityRepo.findByCreatedAtBetween(startTime, endTime);
                if (CollectionUtils.isNotEmpty(onlineBrokers)) {
                    for (BrokerEntity entity : onlineBrokers) {
                        if (log.isDebugEnabled()) {
                            log.debug("{} find online broker id: {}, lastHeartbeat:{}", TASK_NAME, entity.getBrokerId(), LocalTimeUtils.format(entity.getLastHeartbeat(), Formatters.YMD_HMS));
                        }
                        for (NodeListener listener : listeners) {
                            listener.event(new NodeEvent(node(entity), NodeEvent.Type.ONLINE));
                        }
                    }
                }
                lastCheckTime = endTime;
            } catch (Exception e) {
                log.error("{} check fail", TASK_NAME, e);
            }
        }

    }

    private class NodeOfflineCheckTask extends TimerTask {

        private static final String TASK_NAME = "[NodeOfflineCheckTask]";

        LocalDateTime lastCheckTime = TimeUtils.currentLocalDateTime().plusSeconds(-2 * heartbeatTimeout.getSeconds());

        @Override
        public void run() {
            try {
                LocalDateTime startTime = lastCheckTime;
                LocalDateTime endTime = TimeUtils.currentLocalDateTime().plusSeconds(-heartbeatTimeout.getSeconds());
                if (log.isDebugEnabled()) {
                    log.debug("{} checkOffline start:{} end:{}", TASK_NAME, LocalTimeUtils.format(startTime, Formatters.YMD_HMS), LocalTimeUtils.format(endTime, Formatters.YMD_HMS));
                }
                List<BrokerEntity> offlineBrokers = brokerEntityRepo.findByLastHeartbeatBetween(startTime, endTime);
                if (CollectionUtils.isNotEmpty(offlineBrokers)) {
                    for (BrokerEntity entity : offlineBrokers) {
                        if (log.isDebugEnabled()) {
                            log.debug("{} find offline broker id: {}, lastHeartbeat:{}", TASK_NAME, entity.getBrokerId(), LocalTimeUtils.format(entity.getLastHeartbeat(), Formatters.YMD_HMS));
                        }
                        for (NodeListener listener : listeners) {
                            listener.event(new NodeEvent(node(entity), NodeEvent.Type.OFFLINE));
                        }
                    }
                }
                lastCheckTime = endTime;
            } catch (Exception e) {
                log.error("{} check fail", TASK_NAME, e);
            }
        }
    }


}
