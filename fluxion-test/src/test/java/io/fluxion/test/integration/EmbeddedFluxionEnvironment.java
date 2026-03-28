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

import io.fluxion.server.core.broker.Broker;
import io.fluxion.test.integration.executors.CounterExecutor;
import io.fluxion.test.integration.executors.FailingExecutor;
import io.fluxion.test.integration.executors.SimpleTestExecutor;
import io.fluxion.worker.core.Worker;
import io.fluxion.worker.springboot.starter.processor.event.WorkerReadyEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;

/**
 * 内嵌 Fluxion 环境启动器
 * 在测试环境中启动内嵌的 Broker 和 Worker
 * <p>
 * 注意：CQRS Command/Query Handler 由 Limbo CQRS 自动扫描注册
 *
 * @author Devil
 */
@Slf4j
@Component
public class EmbeddedFluxionEnvironment implements ApplicationRunner {

    @Autowired
    private Broker broker;

    @Autowired
    private Worker worker;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private volatile boolean started = false;

    @Override
    public void run(ApplicationArguments args) {
        start();
    }

    /**
     * 启动内嵌环境
     */
    public synchronized void start() {
        if (started) {
            return;
        }

        log.info("[EmbeddedFluxionEnvironment] Starting embedded environment...");

        try {
            // 启动 Broker
            log.info("[EmbeddedFluxionEnvironment] Starting Broker...");
            broker.start();
            log.info("[EmbeddedFluxionEnvironment] Broker started successfully");

            // 等待 Broker 完全就绪
            log.info("[EmbeddedFluxionEnvironment] Waiting for Broker to be ready...");
            Thread.sleep(3000);

            // 启动 Worker（通过发布 WorkerReadyEvent 触发）
            if (eventPublisher != null) {
                log.info("[EmbeddedFluxionEnvironment] Publishing WorkerReadyEvent...");
                try {
                    eventPublisher.publishEvent(new WorkerReadyEvent());
                    log.info("[EmbeddedFluxionEnvironment] Worker started successfully");
                } catch (Exception e) {
                    log.warn("[EmbeddedFluxionEnvironment] Worker start failed (may retry later): {}", e.getMessage());
                }
            }

            started = true;
            log.info("[EmbeddedFluxionEnvironment] Embedded environment started successfully");
        } catch (Exception e) {
            log.error("[EmbeddedFluxionEnvironment] Failed to start embedded environment", e);
            throw new RuntimeException("Failed to start embedded environment", e);
        }
    }

    /**
     * 停止内嵌环境
     */
    @PreDestroy
    public synchronized void stop() {
        if (!started) {
            return;
        }

        log.info("[EmbeddedFluxionEnvironment] Stopping embedded environment...");

        try {
            // 停止 Worker
            if (worker != null) {
                log.info("[EmbeddedFluxionEnvironment] Stopping Worker...");
                worker.stop();
                log.info("[EmbeddedFluxionEnvironment] Worker stopped");
            }

            // 停止 Broker
            if (broker != null) {
                log.info("[EmbeddedFluxionEnvironment] Stopping Broker...");
                broker.stop();
                log.info("[EmbeddedFluxionEnvironment] Broker stopped");
            }

            started = false;
            log.info("[EmbeddedFluxionEnvironment] Embedded environment stopped");
        } catch (Exception e) {
            log.error("[EmbeddedFluxionEnvironment] Error stopping embedded environment", e);
        }
    }

    /**
     * 清理测试数据
     */
    public void clearData() {
        log.info("[EmbeddedFluxionEnvironment] Clearing test data...");

        // 清理 Executor 的执行记录
        SimpleTestExecutor.clearRecords();
        FailingExecutor.clearRecords();
        CounterExecutor.clearRecords();
        CounterExecutor.resetGlobalCounter();

        log.info("[EmbeddedFluxionEnvironment] Test data cleared");
    }

    /**
     * 等待环境就绪
     *
     * @param timeoutMillis 超时时间（毫秒）
     * @return 是否就绪
     */
    public boolean waitForReady(long timeoutMillis) throws InterruptedException {
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            if (started && isBrokerReady() && isWorkerReady()) {
                return true;
            }
            Thread.sleep(100);
        }

        return false;
    }

    /**
     * 检查 Broker 是否就绪
     */
    private boolean isBrokerReady() {
        try {
            return broker != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查 Worker 是否就绪
     */
    private boolean isWorkerReady() {
        try {
            return worker != null;
        } catch (Exception e) {
            return false;
        }
    }

    public Broker getBroker() {
        return broker;
    }

    public Worker getWorker() {
        return worker;
    }

    public boolean isStarted() {
        return started;
    }
}
