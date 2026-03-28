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

package io.fluxion.test.integration;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.time.Duration;

/**
 * 集成测试基类
 * 提供内嵌环境和常用测试工具
 *
 * @author Devil
 */
@Slf4j
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = {TestApplication.TestConfig.class})
public abstract class BaseIntegrationTest {

    @Autowired
    protected EmbeddedFluxionEnvironment embeddedEnvironment;

    @Autowired
    protected TestDataFactory testDataFactory;

    protected static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    protected static final Duration SHORT_TIMEOUT = Duration.ofSeconds(5);
    protected static final Duration LONG_TIMEOUT = Duration.ofSeconds(60);

    @BeforeEach
    void setUp() throws InterruptedException {
        // 确保环境已启动
        if (!embeddedEnvironment.isStarted()) {
            embeddedEnvironment.start();
        }

        // 等待环境就绪
        boolean ready = embeddedEnvironment.waitForReady(10000);
        if (!ready) {
            throw new IllegalStateException("Embedded environment did not become ready in time");
        }

        // 清理测试数据
        embeddedEnvironment.clearData();
        testDataFactory.clearAll();

        log.info("[BaseIntegrationTest] Test setup complete");
    }

    @AfterEach
    void tearDown() {
        // 清理测试数据
        embeddedEnvironment.clearData();
        testDataFactory.clearAll();

        log.info("[BaseIntegrationTest] Test teardown complete");
    }

    /**
     * 等待指定时间
     */
    protected void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while sleeping", e);
        }
    }

    /**
     * 等待条件满足
     */
    protected boolean waitForCondition(java.util.function.BooleanSupplier condition,
                                        Duration timeout,
                                        Duration pollInterval) {
        long endTime = System.currentTimeMillis() + timeout.toMillis();

        while (System.currentTimeMillis() < endTime) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(pollInterval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting", e);
            }
        }

        return false;
    }

    /**
     * 等待条件满足（默认轮询间隔 100ms）
     */
    protected boolean waitForCondition(java.util.function.BooleanSupplier condition, Duration timeout) {
        return waitForCondition(condition, timeout, Duration.ofMillis(100));
    }
}
