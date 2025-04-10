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

package io.fluxion.server.start.config;

import io.fluxion.remote.core.client.server.AbstractClientServer;
import io.fluxion.remote.core.client.server.ClientHandler;
import io.fluxion.remote.core.client.server.ClientServer;
import io.fluxion.remote.core.client.server.ClientServerConfig;
import io.fluxion.remote.core.client.server.ClientServerFactory;
import io.fluxion.remote.core.constants.Protocol;
import io.fluxion.remote.core.utils.NetUtils;
import io.fluxion.server.core.broker.Broker;
import io.fluxion.server.core.broker.BrokerClientHandler;
import io.fluxion.server.core.broker.BrokerManger;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.axonframework.spring.event.AxonStartedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.util.Assert;

import javax.annotation.Resource;

/**
 * @author Devil
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({BrokerProperties.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.ANY)
public class BrokerConfiguration {

    @Resource
    private BrokerProperties brokerProperties;

    @Resource
    private BrokerManger brokerManger;

    @Bean
    public Broker broker() {
        Integer port = brokerProperties.getPort();
        // 优先使用指定的 host，如未指定则自动寻找本机 IP
        String host = brokerProperties.getHost();
        if (StringUtils.isEmpty(host)) {
            host = NetUtils.getLocalIp();
        }
        Assert.isTrue(port > 0, "port must be a positive integer in range 1 ~ 65534");

        // ClientServer
        ClientServerFactory factory = ClientServerFactory.instance();
        ClientHandler clientHandler = new BrokerClientHandler();
        ClientServerConfig clientServerConfig = new ClientServerConfig(port, clientHandler);
        AbstractClientServer clientServer = factory.create(clientServerConfig);

        return new BrokerStarter(
            brokerProperties.getProtocol(), host, port,
            brokerManger, clientServer
        );
    }

    private static class BrokerStarter extends Broker {

        BrokerStarter(Protocol protocol, String host, int port, BrokerManger brokerManger,
                      ClientServer clientServer) {
            super(protocol, host, port, brokerManger, clientServer);
        }

        @Override
        @EventListener(AxonStartedEvent.class)
        public void start() {
            super.start();
        }
    }

}
