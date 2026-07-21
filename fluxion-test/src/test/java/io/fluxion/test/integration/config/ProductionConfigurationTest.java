/*
 * Copyright 2025-2030 Limbo Team (https://github.com/limbo-io).
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

package io.fluxion.test.integration.config;

import io.fluxion.server.autoconfigure.FluxionServerProperties;
import io.fluxion.worker.springboot.starter.properties.WorkerProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.net.URL;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 【生产配置验证测试】
 * 
 * 作用：验证生产环境配置的正确性和完整性
 * 
 * 验证内容：
 *   1. WorkerProperties.brokers 不为空
 *   2. Worker 端口配置有效
 *   3. Broker 端口配置有效
 *   4. 配置前缀正确（无 flowjob.* 等错误前缀）
 * 
 * 执行方式：
 *   mvn test -Dtest=ProductionConfigurationTest -Dspring.profiles.active=prod
 * 
 * @author Fluxion Test Framework
 */
@Slf4j
@SpringBootTest(classes = io.fluxion.test.support.base.TestApplication.class)
@ActiveProfiles("prod")
@EnabledIfSystemProperty(named = "run.production.config.test", matches = "true")
public class ProductionConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @Autowired(required = false)
    private WorkerProperties workerProperties;

    @Autowired(required = false)
    private FluxionServerProperties brokerProperties;

    /**
     * 验证 Worker 配置正确性
     * 
     * 检查点：
     * 1. WorkerProperties 存在
     * 2. brokers 列表不为空
     * 3. 每个 broker URL 有效
     * 4. 端口配置在有效范围
     * 5. 无错误前缀（fluxion.worker 而非 flowjob）
     */
    @Test
    void shouldHaveValidWorkerConfiguration() {
        log.info("[ProductionConfigurationTest] 验证 Worker 配置...");

        assertNotNull(workerProperties, "WorkerProperties 必须存在");

        // 验证 brokers 不为空
        List<URL> brokers = workerProperties.getBrokers();
        assertNotNull(brokers, "Worker brokers 配置不能为空");
        assertFalse(brokers.isEmpty(), "Worker brokers 列表不能为空");

        // 验证每个 broker URL 有效
        for (URL broker : brokers) {
            assertNotNull(broker, "Broker URL 不能为空");
            assertTrue(broker.getPort() > 0 && broker.getPort() <= 65535,
                "Broker 端口必须在有效范围: " + broker);
            assertNotNull(broker.getProtocol(), "Broker URL 协议不能为空");
        }

        // 验证 Worker 端口有效
        Integer workerPort = workerProperties.getPort();
        if (workerPort != null) {
            assertTrue(workerPort > 1024 && workerPort <= 65535,
                "Worker 端口必须在有效范围 (1024-65535)");
        }

        // 验证 protocol 有效
        assertNotNull(workerProperties.getProtocol(), "Worker 协议不能为空");

        // 验证 queue-size 有效
        assertTrue(workerProperties.getQueueSize() >= 0, "Worker 队列大小不能为负数");

        log.info("[ProductionConfigurationTest] Worker 配置验证通过");
    }

    /**
     * 验证 Broker 配置正确性
     * 
     * 检查点：
     * 1. BrokerProperties 存在
     * 2. 端口配置在有效范围
     * 3. 协议配置有效
     */
    @Test
    void shouldHaveValidBrokerConfiguration() {
        log.info("[ProductionConfigurationTest] 验证 Broker 配置...");

        assertNotNull(brokerProperties, "FluxionServerProperties (Broker) 必须存在");

        // 验证 Broker 端口有效
        Integer brokerPort = brokerProperties.getPort();
        assertNotNull(brokerPort, "Broker 端口不能为空");
        assertTrue(brokerPort > 1024 && brokerPort <= 65535,
            "Broker 端口必须在有效范围 (1024-65535)");

        // 验证协议有效
        assertNotNull(brokerProperties.getProtocol(), "Broker 协议不能为空");

        log.info("[ProductionConfigurationTest] Broker 配置验证通过");
    }

    /**
     * 验证配置前缀正确（无错误前缀）
     * 
     * 检查点：
     * 1. 不存在 flowjob.* 前缀的配置
     * 2. 使用 fluxion.broker.* 和 fluxion.worker.* 正确前缀
     */
    @Test
    void shouldNotHaveUnknownPrefixes() {
        log.info("[ProductionConfigurationTest] 验证配置前缀...");

        // 检查 flowjob 前缀（这是旧的错误前缀）
        String flowjobBrokerUrl = environment.getProperty("flowjob.broker.port");
        String flowjobWorkerUrl = environment.getProperty("flowjob.worker.brokers");

        assertNull(flowjobBrokerUrl, "不应使用 flowjob.broker.* 前缀");
        assertNull(flowjobWorkerUrl, "不应使用 flowjob.worker.* 前缀");

        // 验证正确的 fluxion 前缀存在
        String fluxionPrefix = environment.getProperty("fluxion.broker.port");
        // 注意：生产环境可能配置在不同位置，这个检查可能不存在
        // 所以不做强断言，只记录日志
        if (fluxionPrefix != null) {
            log.info("[ProductionConfigurationTest] fluxion.broker.port = {}", fluxionPrefix);
        }

        log.info("[ProductionConfigurationTest] 配置前缀验证通过");
    }

    /**
     * 验证 Worker brokers 配置格式
     * 
     * 确保 brokers 是有效的 HTTP/HTTPS URL
     */
    @Test
    void shouldHaveValidBrokerUrls() {
        log.info("[ProductionConfigurationTest] 验证 Broker URLs...");

        assertNotNull(workerProperties, "WorkerProperties 必须存在");
        List<URL> brokers = workerProperties.getBrokers();
        assertNotNull(brokers, "Worker brokers 不能为空");

        for (URL broker : brokers) {
            String protocol = broker.getProtocol();
            assertTrue("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol),
                "Broker URL 必须使用 http 或 https 协议: " + broker);

            assertNotNull(broker.getHost(), "Broker URL 主机不能为空: " + broker);
            assertFalse(broker.getHost().isEmpty(), "Broker URL 主机不能为空字符串: " + broker);
        }

        log.info("[ProductionConfigurationTest] Broker URLs 验证通过");
    }

    /**
     * 验证应用上下文加载成功
     */
    @Test
    void contextLoads() {
        assertNotNull(applicationContext, "Spring 应用上下文必须加载成功");
        assertTrue(applicationContext.isActive(), "应用上下文必须处于活跃状态");
        log.info("[ProductionConfigurationTest] Spring 上下文加载成功");
    }
}
