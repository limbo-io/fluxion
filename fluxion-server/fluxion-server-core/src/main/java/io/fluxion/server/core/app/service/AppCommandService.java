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

package io.fluxion.server.core.app.service;

import io.fluxion.remote.core.api.request.broker.BrokerPingRequest;
import io.fluxion.remote.core.client.Client;
import io.fluxion.server.core.app.App;
import io.fluxion.server.core.app.cmd.AppBrokerElectCmd;
import io.fluxion.server.core.app.cmd.AppRegisterCmd;
import io.fluxion.server.core.app.converter.AppEntityConverter;
import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.broker.BrokerManger;
import io.fluxion.server.core.broker.BrokerNode;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.dao.entity.AppEntity;
import io.fluxion.server.infrastructure.dao.repository.AppEntityRepo;
import io.fluxion.server.infrastructure.exception.ErrorCode;
import io.fluxion.server.infrastructure.exception.PlatformException;
import io.fluxion.server.infrastructure.id.cmd.IDGenerateCmd;
import io.fluxion.server.infrastructure.id.data.IDType;
import io.fluxion.server.infrastructure.lock.DistributedLock;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.fluxion.remote.core.constants.BrokerRemoteConstant.API_BROKER_PING;

/**
 * @author Devil
 */
@Slf4j
@Service
public class AppCommandService {

    private static final int ELECT_RETRY_TIMES = 5;

    private static final String ELECT_LOCK = "%s_app_broker_elect";

    @Resource
    private AppEntityRepo appEntityRepo;

    @Resource
    private BrokerManger brokerManger;

    @Resource
    private DistributedLock distributedLock;

    @CommandHandler
    public AppRegisterCmd.Response handle(AppRegisterCmd cmd) {
        AppEntity entity = appEntityRepo.findByAppName(cmd.getAppName()).orElse(null);
        if (entity == null) {
            String appId = Cmd.send(new IDGenerateCmd(IDType.APP)).getId();
            entity = new AppEntity();
            entity.setBrokerId(StringUtils.EMPTY);
            entity.setAppName(cmd.getAppName());
            entity.setAppId(appId);
            appEntityRepo.saveAndFlush(entity);
        }
        BrokerNode node = Cmd.send(new AppBrokerElectCmd(entity.getAppId())).getBroker();
        App app = AppEntityConverter.convert(entity, node, brokerManger.allAlive());
        return new AppRegisterCmd.Response(app);
    }

    @CommandHandler
    public AppBrokerElectCmd.Response handle(AppBrokerElectCmd cmd) {
        String appId = cmd.getAppId();
        List<BrokerNode> nodes = brokerManger.allAlive();
        String lockName = String.format(ELECT_LOCK, appId);
        for (int i = 0; i < ELECT_RETRY_TIMES; i++) {
            // 锁外先做一次，减少DB锁
            AppEntity entity = appEntityRepo.findById(appId)
                .orElseThrow(() -> new IllegalArgumentException("can't find app by id:" + appId));
            BrokerNode node = brokerManger.get(BrokerContext.broker().id());
            if (entity.getBrokerId().equals(BrokerContext.broker().id())) {
                return new AppBrokerElectCmd.Response(node, false, brokerManger.allAlive());
            }
            boolean locked = distributedLock.lock(lockName, 10000);
            if (!locked) {
                try {
                    Thread.sleep(1000);
                } catch (Exception ignored) {
                }
                continue;
            }
            try {
                // 如果app当前绑定节点和worker请求的broker节点为同个节点则直接返回
                entity = appEntityRepo.findById(appId)
                    .orElseThrow(() -> new IllegalArgumentException("can't find app by id:" + appId));
                node = brokerManger.get(BrokerContext.broker().id());
                if (entity.getBrokerId().equals(BrokerContext.broker().id())) {
                    return new AppBrokerElectCmd.Response(node, false, brokerManger.allAlive());
                }
                // 已经绑定其它节点为broker，判断其是否存活
                if (StringUtils.isNotBlank(entity.getBrokerId())) {
                    Boolean pong = BrokerContext.call(API_BROKER_PING, node.host(), node.port(), new BrokerPingRequest());
                    if (BooleanUtils.isTrue(pong)) {
                        return new AppBrokerElectCmd.Response(node, true, brokerManger.allAlive());
                    }
                    String failNodeId = node.id();
                    nodes = nodes.stream().filter(n -> !Objects.equals(n.id(), failNodeId)).collect(Collectors.toList());
                }
                // 节点非存活状态，首次选举或者重新进行选举
                BrokerNode elect = brokerManger.allAlive().stream().min(Comparator.comparing(BrokerNode::load)).orElse(null);
                if (!BrokerContext.broker().id().equals(elect.id())) {
                    // 没选中当前节点，则ping一下确认选中的节点存活
                    Boolean pong = BrokerContext.call(API_BROKER_PING, elect.host(), elect.port(), new BrokerPingRequest());
                    if (BooleanUtils.isNotTrue(pong)) {
                        nodes = nodes.stream().filter(n -> !Objects.equals(n.id(), elect.id())).collect(Collectors.toList());
                        continue;
                    }
                }
                // 选举成功
                entity.setBrokerId(elect.id());
                appEntityRepo.saveAndFlush(entity);
                return new AppBrokerElectCmd.Response(elect, true, brokerManger.allAlive());
            } catch (Exception e) {
                log.error("App broker elect fail appId:{}", appId, e);
            } finally {
                distributedLock.unlock(lockName);
            }
        }
        throw new PlatformException(ErrorCode.SYSTEM_ERROR, "broker elect failed appId: " + appId);
    }

}
