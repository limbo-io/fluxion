/*
 * Copyright 2025-2030 fluxion-io Team (https://github.com/fluxion-io).
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

import io.fluxion.common.thread.NamedThreadFactory;
import io.fluxion.common.utils.MD5Utils;
import io.fluxion.common.utils.time.Formatters;
import io.fluxion.common.utils.time.LocalDateTimeUtils;
import io.fluxion.common.utils.time.LocalTimeUtils;
import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.remote.core.constants.Protocol;
import io.fluxion.server.core.broker.cmd.BucketRebalanceCmd;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.dao.entity.BrokerEntity;
import io.fluxion.server.infrastructure.dao.repository.BrokerEntityRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 内存中缓存的 broker节点信息
 *
 * @author Devil
 * @since 2022/7/20
 */
@Slf4j
@Component
public class BrokerManger {

    @Resource
    private BrokerEntityRepo brokerEntityRepo;

    /**
     * 心跳时间间隔，毫秒
     */
    private Duration heartbeatInterval;

    /**
     * 心跳超时时间，毫秒
     */
    private Duration heartbeatTimeout;

    private ScheduledExecutorService scheduledExecutorService;

    private static final Map<String, BrokerNode> NODES = new ConcurrentHashMap<>();

    private String version = "";

    public void start() {
        this.heartbeatInterval = Duration.ofMillis(3000);
        this.heartbeatTimeout = Duration.ofMillis(10000);
        this.scheduledExecutorService = new ScheduledThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 4,
            NamedThreadFactory.newInstance("FluxionBrokerManger")
        );

        // 放入本地
        BrokerNode node = BrokerContext.broker().node();
        NODES.put(node.id(), node);

        // 处理bucket
        Cmd.send(new BucketRebalanceCmd());
        // 开启定时任务 维持心跳
        scheduledExecutorService.scheduleAtFixedRate(
            new HeartbeatTask(), 0, heartbeatInterval.toMillis(), TimeUnit.MILLISECONDS
        );

        // 开启定时任务，监听broker心跳情况
        scheduledExecutorService.scheduleAtFixedRate(
            new NodeOnlineCheckTask(), 0, heartbeatTimeout.toMillis(), TimeUnit.MILLISECONDS
        );
        scheduledExecutorService.scheduleAtFixedRate(
            new NodeOfflineCheckTask(), 0, heartbeatTimeout.toMillis(), TimeUnit.MILLISECONDS
        );
    }

    /**
     * 节点上线
     */
    public void online(BrokerNode node) {
        NODES.putIfAbsent(node.id(), node);
        changeVersion();
        if (log.isDebugEnabled()) {
            log.debug("[BrokerManger] online {}", NODES);
        }
    }

    /**
     * 节点下线
     */
    public void offline(BrokerNode node) {
        NODES.remove(node.id());
        changeVersion();
        if (log.isDebugEnabled()) {
            log.debug("[BrokerManger] offline {}", NODES);
        }
    }

    private void changeVersion() {
        String brokerIdStr = allAlive().stream().map(BrokerNode::id).sorted().collect(Collectors.joining(","));
        version = MD5Utils.md5(brokerIdStr);
    }

    public String version() {
        return version;
    }

    public BrokerNode get(String id) {
        return NODES.get(id);
    }

    /**
     * 所有存活节点
     */
    public List<BrokerNode> allAlive() {
        if (NODES.isEmpty() && log.isDebugEnabled()) {
            log.debug("[BrokerManger] allAlive {}", NODES);
        }
        return new ArrayList<>(NODES.values());
    }

    public void stop() {
        scheduledExecutorService.shutdown();
    }

    private BrokerNode node(BrokerEntity entity) {
        return new BrokerNode(
            Protocol.parse(entity.getProtocol()),
            entity.getId().getHost(), entity.getId().getPort(),
            entity.getBrokerLoad()
        );
    }

    private BrokerEntity entity(BrokerNode node) {
        BrokerEntity entity = new BrokerEntity();
        entity.setId(new BrokerEntity.ID(node.host(), node.port()));
        entity.setProtocol(node.protocol().getValue());
        entity.setBrokerLoad(node.load());
        entity.setLastHeartbeatAt(TimeUtils.currentLocalDateTime());
        return entity;
    }

    private class HeartbeatTask extends TimerTask {
        private static final String TASK_NAME = "[HeartbeatTask]";

        @Override
        public void run() {
            BrokerNode node = BrokerContext.broker().node();
            BrokerEntity entity = entity(node);
            try {
                brokerEntityRepo.saveAndFlush(entity);
                if (log.isDebugEnabled()) {
                    log.debug("{} send heartbeat id: {} time:{}",
                        TASK_NAME, node.id(), LocalTimeUtils.format(entity.getLastHeartbeatAt(), Formatters.YMD_HMS)
                    );
                }
            } catch (Exception e) {
                log.error("{} send heartbeat fail", TASK_NAME, e);
            }
        }

    }

    private class NodeOnlineCheckTask extends TimerTask {

        private static final String TASK_NAME = "[NodeOnlineCheckTask]";

        LocalDateTime lastCheckAt = LocalDateTimeUtils.parse("2000-01-01 00:00:00", Formatters.YMD_HMS);

        @Override
        public void run() {
            try {
                LocalDateTime startTime = lastCheckAt;
                LocalDateTime endTime = TimeUtils.currentLocalDateTime();
                if (log.isDebugEnabled()) {
                    log.info("{} checkOnline start:{} end:{}", TASK_NAME, LocalTimeUtils.format(startTime, Formatters.YMD_HMS), LocalTimeUtils.format(endTime, Formatters.YMD_HMS));
                }
                List<BrokerEntity> onlineBrokers = brokerEntityRepo.findByCreatedAtBetween(startTime, endTime);
                if (CollectionUtils.isNotEmpty(onlineBrokers)) {
                    for (BrokerEntity entity : onlineBrokers) {
                        if (log.isDebugEnabled()) {
                            log.debug("{} find online broker id: {}, lastHeartbeat:{}",
                                TASK_NAME, entity.getId(), LocalTimeUtils.format(entity.getLastHeartbeatAt(), Formatters.YMD_HMS)
                            );
                        }
                        online(node(entity));
                    }
                }
                lastCheckAt = endTime;
            } catch (Exception e) {
                log.error("{} check fail", TASK_NAME, e);
            }
        }

    }

    private class NodeOfflineCheckTask extends TimerTask {

        private static final String TASK_NAME = "[NodeOfflineCheckTask]";

        LocalDateTime lastCheckAt = TimeUtils.currentLocalDateTime().plusSeconds(-2 * heartbeatTimeout.getSeconds());

        @Override
        public void run() {
            try {
                LocalDateTime startTime = lastCheckAt;
                LocalDateTime endTime = TimeUtils.currentLocalDateTime().plusSeconds(-heartbeatTimeout.getSeconds());
                if (log.isDebugEnabled()) {
                    log.debug("{} checkOffline start:{} end:{}", TASK_NAME, LocalTimeUtils.format(startTime, Formatters.YMD_HMS), LocalTimeUtils.format(endTime, Formatters.YMD_HMS));
                }
                List<BrokerEntity> offlineBrokers = brokerEntityRepo.findByLastHeartbeatAtBetween(startTime, endTime);
                if (CollectionUtils.isNotEmpty(offlineBrokers)) {
                    for (BrokerEntity entity : offlineBrokers) {
                        if (log.isDebugEnabled()) {
                            log.debug("{} find offline broker id: {}, lastHeartbeat:{}",
                                TASK_NAME, entity.getId(), LocalTimeUtils.format(entity.getLastHeartbeatAt(), Formatters.YMD_HMS)
                            );
                        }
                        offline(node(entity));
                    }
                }
                lastCheckAt = endTime;
            } catch (Exception e) {
                log.error("{} check fail", TASK_NAME, e);
            }
        }
    }

}
