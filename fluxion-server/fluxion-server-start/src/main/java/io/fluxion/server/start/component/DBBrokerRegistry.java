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

import io.fluxion.common.utils.time.Formatters;
import io.fluxion.common.utils.time.LocalTimeUtils;
import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.server.core.cluster.*;
import io.fluxion.server.infrastructure.dao.entity.BrokerEntity;
import io.fluxion.server.infrastructure.dao.repository.BrokerEntityRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

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
     * 心跳超时时间，毫秒
     */
    private Duration heartbeatTimeout;

    /**
     * 心跳时间间隔，毫秒
     */
    private Duration heartbeatInterval;

    // todo @d
    public DBBrokerRegistry(long heartbeatInterval, long heartbeatTimeout,
                            BrokerEntityRepo brokerEntityRepo) {
        this.heartbeatInterval = Duration.ofMillis(heartbeatInterval);
        this.heartbeatTimeout = Duration.ofMillis(heartbeatTimeout);
        this.brokerEntityRepo = brokerEntityRepo;
    }

    private final List<NodeListener> listeners = new ArrayList<>();

    @Override
    public void register(Node node) {
        // 新建
        String protocols = protocols(node.protocols());
        BrokerEntity broker = new BrokerEntity();
        broker.setBrokerId(node.id());
        broker.setProtocols(protocols);
        broker.setLastHeartbeat(TimeUtils.currentLocalDateTime());
        brokerEntityRepo.saveAndFlush(broker);

        // 开启定时任务 维持心跳
        new Timer().schedule(new HeartbeatTask(node.id(), protocols), 0, heartbeatInterval.toMillis());

        // 开启定时任务，监听broker心跳情况
        new Timer().schedule(new NodeOnlineCheckTask(), 0, heartbeatTimeout.toMillis());
        new Timer().schedule(new NodeOfflineCheckTask(), 0, heartbeatTimeout.toMillis());
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
        // todo @d
    }

    private Node node(BrokerEntity entity) {
        return new Node(entity.getBrokerId(), protocols(entity.getProtocols()));
    }

    private String protocols(List<NodeProtocol> protocols) {
        return null; // todo @d
    }

    private List<NodeProtocol> protocols(String protocols) {
        return null; // todo @d
    }

    private class HeartbeatTask extends TimerTask {
        private static final String TASK_NAME = "[HeartbeatTask]";
        private final String id;
        private final String protocols;

        public HeartbeatTask(String id, String protocols) {
            this.id = id;
            this.protocols = protocols;
        }

        @Override
        public void run() {
            try {
                LocalDateTime now = TimeUtils.currentLocalDateTime();
                BrokerEntity broker = new BrokerEntity();
                broker.setBrokerId(id);
                broker.setProtocols(protocols);
                broker.setLastHeartbeat(now);
                brokerEntityRepo.saveAndFlush(broker);
                if (log.isDebugEnabled()) {
                    log.debug("{} send heartbeat id: {} protocols:{} time:{}", TASK_NAME, id, protocols, LocalTimeUtils.format(now, Formatters.YMD_HMS));
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
                            log.debug("{} find online broker id: {}, protocols: {} lastHeartbeat:{}", TASK_NAME, entity.getBrokerId(), entity.getProtocols(), LocalTimeUtils.format(entity.getLastHeartbeat(), Formatters.YMD_HMS));
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
                            log.debug("{} find offline broker id: {}, protocols: {} lastHeartbeat:{}", TASK_NAME, entity.getBrokerId(), entity.getProtocols(), LocalTimeUtils.format(entity.getLastHeartbeat(), Formatters.YMD_HMS));
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
