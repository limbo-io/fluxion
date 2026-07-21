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

package io.fluxion.test.support.environment;

import io.fluxion.server.core.broker.Broker;
import io.fluxion.test.support.executors.CounterExecutor;
import io.fluxion.test.support.executors.FailingExecutor;
import io.fluxion.test.support.executors.SimpleTestExecutor;
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
 * 【测试基础设施 - 嵌入式环境】
 * 
 * 作用：在测试环境中自动启动内嵌的 Broker 和 Worker，实现真正的端到端集成测试
 *       无需依赖外部服务，一个测试类即可完成完整的调度链路验证
 * 
 * 启动顺序：
 *   1. Spring Boot 启动
 *   2. Broker 启动 (端口监听、任务调度器初始化)
 *   3. Worker 启动 (通过 WorkerReadyEvent 触发注册)
 *   4. 测试执行
 * 
 * 使用方式：
 *   - 继承 {@link io.fluxion.test.support.base.BaseIntegrationTest} 自动获得此环境
 *   - 或通过 @Autowired 注入使用
 * 
 * 注意：CQRS Command/Query Handler 由 Limbo CQRS 框架自动扫描注册
 * 
 * @author Devil
 * @see io.fluxion.test.support.base.BaseIntegrationTest
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

    /**
     * Spring Boot 启动后自动执行
     */
    @Override
    public void run(ApplicationArguments args) {
        start();
    }

    /**
     * 启动内嵌环境
     * 
     * 线程安全：使用 synchronized 防止并发启动
     */
    public synchronized void start() {
        if (started) {
            return;
        }

        log.info("[EmbeddedFluxionEnvironment] ========== 启动内嵌测试环境 ==========");

        try {
            // ===== 步骤1: 启动 Broker =====
            // Broker 负责任务调度、Worker管理、状态维护
            log.info("[EmbeddedFluxionEnvironment] [1/2] 启动 Broker...");
            broker.start();
            log.info("[EmbeddedFluxionEnvironment] Broker 启动完成");

            // 等待 Broker 完全就绪（初始化数据库连接、调度器等）
            Thread.sleep(3000);

            // ===== 步骤2: 启动 Worker =====
            // 通过发布 WorkerReadyEvent 触发 Worker 自动注册到 Broker
            if (eventPublisher != null) {
                log.info("[EmbeddedFluxionEnvironment] [2/2] 触发 Worker 注册...");
                try {
                    eventPublisher.publishEvent(new WorkerReadyEvent());
                    log.info("[EmbeddedFluxionEnvironment] Worker 注册完成");
                } catch (Exception e) {
                    log.warn("[EmbeddedFluxionEnvironment] Worker 启动失败（可能需要重试）: {}", e.getMessage());
                }
            }

            started = true;
            log.info("[EmbeddedFluxionEnvironment] ========== 内嵌环境启动成功 ==========");
        } catch (Exception e) {
            log.error("[EmbeddedFluxionEnvironment] 环境启动失败", e);
            throw new RuntimeException("Failed to start embedded environment", e);
        }
    }

    /**
     * 停止内嵌环境
     * 
     * 触发时机：
     *   - Spring 容器销毁时（@PreDestroy）
     *   - 测试结束后清理资源
     * 
     * 关闭顺序：先 Worker 后 Broker，确保任务优雅终止
     */
    @PreDestroy
    public synchronized void stop() {
        if (!started) {
            return;
        }

        log.info("[EmbeddedFluxionEnvironment] ========== 停止内嵌测试环境 ==========");

        try {
            // 先停止 Worker（停止心跳、任务执行）
            if (worker != null) {
                log.info("[EmbeddedFluxionEnvironment] 停止 Worker...");
                worker.stop();
                log.info("[EmbeddedFluxionEnvironment] Worker 已停止");
            }

            // 再停止 Broker（停止调度、关闭端口）
            if (broker != null) {
                log.info("[EmbeddedFluxionEnvironment] 停止 Broker...");
                broker.stop();
                log.info("[EmbeddedFluxionEnvironment] Broker 已停止");
            }

            started = false;
            log.info("[EmbeddedFluxionEnvironment] ========== 内嵌环境已停止 ==========");
        } catch (Exception e) {
            log.error("[EmbeddedFluxionEnvironment] 环境停止时发生错误", e);
        }
    }

    /**
     * 清理测试数据
     * 
     * 调用时机：每个测试方法结束后，用于隔离测试用例之间的数据影响
     */
    public void clearData() {
        log.info("[EmbeddedFluxionEnvironment] 清理测试数据...");

        // 清理各执行器的执行记录（静态变量，需手动清理）
        SimpleTestExecutor.clearRecords();
        FailingExecutor.clearRecords();
        CounterExecutor.clearRecords();
        CounterExecutor.resetGlobalCounter();

        log.info("[EmbeddedFluxionEnvironment] 测试数据清理完成");
    }

    /**
     * 等待环境就绪（阻塞方法）
     * 
     * @param timeoutMillis 最大等待时间（毫秒）
     * @return true=就绪, false=超时
     * @throws InterruptedException 线程中断
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
     * 检查 Broker 是否就绪（简单检查对象是否存在）
     */
    private boolean isBrokerReady() {
        try {
            return broker != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查 Worker 是否就绪（简单检查对象是否存在）
     */
    private boolean isWorkerReady() {
        try {
            return worker != null;
        } catch (Exception e) {
            return false;
        }
    }

    // ===== Getter 方法 =====

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
