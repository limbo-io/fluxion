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

import io.fluxion.remote.core.utils.NetUtils;
import io.fluxion.server.core.broker.Broker;
import io.fluxion.server.core.broker.BrokerManger;
import io.fluxion.server.core.broker.DBBrokerRegistry;
import io.fluxion.server.infrastructure.lock.DistributedLock;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
    private DistributedLock distributedLock;

    @Resource
    private DBBrokerRegistry brokerRegistry;

    @Resource
    private BrokerManger brokerManger;

    @Value("${server.port:8080}")
    private Integer httpServerPort;

    @Bean
    public Broker broker() {
        Integer port = brokerProperties.getPort() != null ? brokerProperties.getPort() : httpServerPort;
        // 优先使用指定的 host，如未指定则自动寻找本机 IP
        String host = brokerProperties.getHost();
        if (StringUtils.isEmpty(host)) {
            host = NetUtils.getLocalIp();
        }
        Assert.isTrue(port > 0, "port must be a positive integer in range 1 ~ 65534");
        Broker broker = new Broker(
            brokerProperties.getProtocol(), host, port,
            brokerRegistry, brokerManger, distributedLock
        );
        broker.start();
        return broker;
    }

}
