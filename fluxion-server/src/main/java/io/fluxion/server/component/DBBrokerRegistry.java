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

package io.fluxion.server.component;

import io.fluxion.common.utils.time.Formatters;
import io.fluxion.common.utils.time.LocalTimeUtils;
import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.core.cluster.NodeEvent;
import io.fluxion.core.cluster.NodeListener;
import io.fluxion.core.cluster.NodeRegistry;
import io.fluxion.core.cqrs.Cmd;
import io.fluxion.core.dao.entity.BrokerEntity;
import io.fluxion.core.dao.repository.BrokerEntityRepo;
import io.fluxion.core.id.cmd.IDGenerateCmd;
import io.fluxion.core.id.data.IDType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.net.URL;
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

    // todo
    public DBBrokerRegistry(long heartbeatInterval, long heartbeatTimeout,
                            BrokerEntityRepo brokerEntityRepo) {
        this.heartbeatInterval = Duration.ofMillis(heartbeatInterval);
        this.heartbeatTimeout = Duration.ofMillis(heartbeatTimeout);
        this.brokerEntityRepo = brokerEntityRepo;
    }

    private final List<NodeListener> listeners = new ArrayList<>();

    @Override
    public void register(String name, URL url) {
        // 开启定时任务 维持心跳
        new Timer().schedule(new HeartbeatTask(name, url), 0, heartbeatInterval.toMillis());

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

    private class HeartbeatTask extends TimerTask {
        private static final String TASK_NAME = "[HeartbeatTask]";
        private final String name;
        private final URL url;

        public HeartbeatTask(String name, URL url) {
            this.name = name;
            this.url = url;
        }

        @Override
        public void run() {
            try {
                String protocol = url.getProtocol();
                String host = url.getHost();
                Integer port = url.getPort();
                BrokerEntity broker = brokerEntityRepo.findByProtocolAndHostAndPort(protocol, host, port);
                LocalDateTime now = TimeUtils.currentLocalDateTime();
                if (broker == null) {
                    String id = Cmd.send(new IDGenerateCmd(IDType.BROKER)).getId();
                    broker = new BrokerEntity();
                    broker.setBrokerId(id);
                    broker.setOnlineTime(now);
                } else if (broker.getLastHeartbeat().plusSeconds(heartbeatTimeout.getSeconds()).isBefore(now)) { // 断线重连
                    broker.setOnlineTime(now);
                }
                broker.setName(name);
                broker.setProtocol(protocol);
                broker.setHost(host);
                broker.setPort(port);
                broker.setLastHeartbeat(now);
                brokerEntityRepo.saveAndFlush(broker);
                if (log.isDebugEnabled()) {
                    log.debug("{} send heartbeat name: {}, host: {}, port: {} time:{}", TASK_NAME, name, host, port, LocalTimeUtils.format(TimeUtils.currentLocalDateTime(), Formatters.YMD_HMS));
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
                List<BrokerEntity> onlineBrokers = brokerEntityRepo.findByOnlineTimeBetween(startTime, endTime);
                if (CollectionUtils.isNotEmpty(onlineBrokers)) {
                    for (BrokerEntity broker : onlineBrokers) {
                        if (log.isDebugEnabled()) {
                            log.debug("{} find online broker name: {}, host: {}, port: {} lastHeartbeat:{}", TASK_NAME, broker.getName(), broker.getHost(), broker.getPort(), LocalTimeUtils.format(broker.getLastHeartbeat(), Formatters.YMD_HMS));
                        }
                        for (NodeListener listener : listeners) {
                            listener.event(new NodeEvent(NodeEvent.Type.ONLINE, broker.getName(), url(broker)));
                        }
                    }
                }
                lastCheckTime = endTime;
            } catch (Exception e) {
                log.error("{} check fail", TASK_NAME, e);
            }
        }

    }

    public static URL url(BrokerEntity po) {
        try {
            return new URL(po.getProtocol(), po.getHost(), po.getPort(), "");
        } catch (Exception e) {
            throw new IllegalStateException("parse worker rpc info error", e);
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
                    for (BrokerEntity broker : offlineBrokers) {
                        if (log.isDebugEnabled()) {
                            log.debug("{} find offline broker name: {}, host: {}, port: {} lastHeartbeat:{}", TASK_NAME, broker.getName(), broker.getHost(), broker.getPort(), LocalTimeUtils.format(broker.getLastHeartbeat(), Formatters.YMD_HMS));
                        }
                        for (NodeListener listener : listeners) {
                            listener.event(new NodeEvent(NodeEvent.Type.OFFLINE, broker.getName(), url(broker)));
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
