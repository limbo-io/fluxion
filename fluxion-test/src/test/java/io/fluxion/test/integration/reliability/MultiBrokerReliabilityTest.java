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

package io.fluxion.test.integration.reliability;

import io.fluxion.server.core.schedule.Schedule;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;
import io.fluxion.test.integration.IntegrationTestBase;
import io.fluxion.test.support.executors.CounterExecutor;
import io.fluxion.test.support.executors.SimpleTestExecutor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 【多 Broker 可靠性回归测试】
 * 
 * 作用：验证分布式调度系统在 Broker 集群环境下的可靠性
 * 
 * 测试场景：
 *   1. 并发抢占 -> 仅创建一个执行实例
 *   2. Broker 重启恢复 -> 执行继续被监控
 *   3. Worker 离线迁移 -> 任务分发到新 Worker
 *   4. 重复分发 -> 幂等（仅创建一个执行）
 * 
 * 执行方式：
 *   mvn test -pl fluxion-test -Dtest=MultiBrokerReliabilityTest
 *   mvn test -pl fluxion-test -Pregression-test
 * 
 * @author Fluxion Test Framework
 */
@Slf4j
@DisplayName("多 Broker 可靠性测试")
public class MultiBrokerReliabilityTest extends IntegrationTestBase {

    @BeforeEach
    void setup() {
        log.info("[MultiBrokerReliabilityTest] ======== 测试准备 ========");
        // 清理测试数据
        CounterExecutor.reset();
        SimpleTestExecutor.reset();
    }

    @AfterEach
    void cleanup() {
        log.info("[MultiBrokerReliabilityTest] ======== 测试清理 ========");
        CounterExecutor.reset();
        SimpleTestExecutor.reset();
    }

    /**
     * 场景1: 并发抢占 -> 仅创建一个执行实例
     * 
     * 验证点：
     * 1. 多个线程同时尝试抢占同一延迟任务
     * 2. 只有一条抢占成功并创建执行
     * 3. 其他抢占因租约冲突而失败
     */
    @Test
    @DisplayName("1. 并发抢占应仅创建一个执行实例 - 数据库锁防止重复")
    void concurrentClaimShouldCreateOnlyOneExecution() throws InterruptedException {
        log.info("[场景1] 并发抢占 -> 仅创建一个执行实例");

        // 创建延迟调度（2秒后触发）
        String executorName = "counterExecutor";
        Schedule schedule = testDataFactory.createExecutorDelaySchedule(
            executorName, 
            System.currentTimeMillis() + 1000, // 1秒后执行
            ChronoUnit.MILLIS
        );

        // 提交调度
        embeddedEnvironment.submitSchedule(schedule);

        // 等待调度就绪
        sleep(Duration.ofMillis(500));

        // 模拟并发抢占：启动多个线程同时尝试抢占
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    // 模拟抢占延迟任务
                    boolean claimed = embeddedEnvironment.tryClaimSchedule(
                        schedule.getId(), 
                        "worker-" + threadId
                    );
                    if (claimed) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    log.warn("线程 {} 抢占失败: {}", threadId, e.getMessage());
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        // 等待所有线程完成
        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "并发抢占应在10秒内完成");

        // 验证：只有一个抢占成功
        log.info("抢占结果 - 成功: {}, 失败: {}", successCount.get(), failureCount.get());
        assertEquals(1, successCount.get(), 
            "并发抢占应仅有一个成功（数据库分布式锁保证）");
        assertEquals(threadCount - 1, failureCount.get(),
            "其他抢占应因锁冲突而失败");

        // 执行应该只被执行一次
        boolean executed = waitForCondition(
            () -> CounterExecutor.getExecutionCount() == 1,
            Duration.ofSeconds(10)
        );
        assertTrue(executed, "任务应在10秒内执行");
        assertEquals(1, CounterExecutor.getExecutionCount(),
            "任务应仅执行一次");

        log.info("[场景1] ✅ 并发抢占验证通过");
    }

    /**
     * 场景2: Broker 重启恢复 -> 执行继续被监控
     * 
     * 验证点：
     * 1. Broker 重启前创建的执行
     * 2. Broker 重启后仍会监控该执行
     * 3. 执行完成状态能被正确记录
     */
    @Test
    @DisplayName("2. Broker 重启后执行仍被监控 - 状态持久化")
    void brokerRestartShouldContinueMonitoringExecution() {
        log.info("[场景2] Broker 重启恢复 -> 执行继续被监控");

        // 创建并提交一个延迟调度
        String executorName = "simpleTestExecutor";
        Schedule schedule = testDataFactory.createExecutorDelaySchedule(
            executorName,
            System.currentTimeMillis() + 500, // 500ms后执行
            ChronoUnit.MILLIS
        );

        String scheduleId = embeddedEnvironment.submitSchedule(schedule);
        log.info("已提交调度: {}", scheduleId);

        // 等待任务执行
        boolean executed = waitForCondition(
            () -> SimpleTestExecutor.getExecutionCount() > 0,
            Duration.ofSeconds(10)
        );
        assertTrue(executed, "任务应在10秒内执行");
        log.info("任务已执行");

        // 模拟 Broker 重启：重新加载执行状态
        embeddedEnvironment.simulateBrokerRestart();
        log.info("模拟 Broker 重启完成");

        // 验证执行状态仍能从数据库加载
        boolean stateRecovered = waitForCondition(
            () -> embeddedEnvironment.isExecutionStateLoaded(scheduleId),
            Duration.ofSeconds(5)
        );
        assertTrue(stateRecovered, "Broker 重启后应恢复执行状态");

        // 验证执行计数未丢失
        assertEquals(1, SimpleTestExecutor.getExecutionCount(),
            "Broker 重启后执行计数应保持一致");

        log.info("[场景2] ✅ Broker 重启恢复验证通过");
    }

    /**
     * 场景3: Worker 离线迁移 -> 任务分发到新 Worker
     * 
     * 验证点：
     * 1. Worker 离线后，其任务应被迁移
     * 2. 新 Worker 应能接收迁移的任务
     * 3. 迁移过程不影响执行
     */
    @Test
    @DisplayName("3. Worker 离线迁移 - 任务分发到新 Worker")
    void workerOfflineShouldMigrateJobsToNewWorker() {
        log.info("[场景3] Worker 离线迁移 -> 任务分发到新 Worker");

        // 注册两个 Worker
        String worker1Id = "worker-old-001";
        String worker2Id = "worker-new-002";

        embeddedEnvironment.registerWorker(worker1Id, true);
        embeddedEnvironment.registerWorker(worker2Id, true);

        // 创建调度
        Schedule schedule = testDataFactory.createExecutorDelaySchedule(
            "simpleTestExecutor",
            System.currentTimeMillis() + 500,
            ChronoUnit.MILLIS
        );

        String scheduleId = embeddedEnvironment.submitSchedule(schedule);
        log.info("已提交调度到 worker1: {}", scheduleId);

        // 模拟 Worker1 离线
        embeddedEnvironment.simulateWorkerOffline(worker1Id);
        log.info("模拟 Worker1 离线");

        // 验证任务被迁移到 Worker2
        boolean migrated = waitForCondition(
            () -> embeddedEnvironment.isJobMigrated(scheduleId, worker2Id),
            Duration.ofSeconds(10)
        );
        assertTrue(migrated, "Worker 离线后任务应迁移到新 Worker");
        log.info("任务已迁移到 Worker2");

        // 验证任务最终执行
        boolean executed = waitForCondition(
            () -> SimpleTestExecutor.getExecutionCount() > 0,
            Duration.ofSeconds(15)
        );
        assertTrue(executed, "迁移的任务应在新 Worker 上执行");

        log.info("[场景3] ✅ Worker 离线迁移验证通过");
    }

    /**
     * 场景4: 重复分发 -> 幂等（仅创建一个执行）
     * 
     * 验证点：
     * 1. 同一任务被多次分发到 Worker
     * 2. Worker 仅处理一次（幂等）
     * 3. 后续重复分发被拒绝
     */
    @Test
    @DisplayName("4. 重复分发幂等性 - 仅创建一个执行")
    void duplicateDispatchShouldBeIdempotent() {
        log.info("[场景4] 重复分发 -> 幂等（仅创建一个执行）");

        // 创建调度
        String executorName = "counterExecutor";
        Schedule schedule = testDataFactory.createExecutorDelaySchedule(
            executorName,
            System.currentTimeMillis() + 200,
            ChronoUnit.MILLIS
        );

        String scheduleId = embeddedEnvironment.submitSchedule(schedule);
        log.info("已提交调度: {}", scheduleId);

        // 等待调度就绪
        sleep(Duration.ofMillis(300));

        // 模拟多次重复分发
        int dispatchCount = 5;
        AtomicInteger acceptedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < dispatchCount; i++) {
            boolean accepted = embeddedEnvironment.dispatchToWorker(
                scheduleId, 
                "default-worker"
            );
            if (accepted) {
                acceptedCount.incrementAndGet();
            } else {
                rejectedCount.incrementAndGet();
            }
        }

        log.info("分发结果 - 接受: {}, 拒绝: {}", acceptedCount.get(), rejectedCount.get());

        // 等待执行完成
        boolean executed = waitForCondition(
            () -> CounterExecutor.getExecutionCount() > 0,
            Duration.ofSeconds(10)
        );
        assertTrue(executed, "任务应被执行");

        // 验证：虽然分发了5次，但只执行1次
        assertEquals(1, CounterExecutor.getExecutionCount(),
            "重复分发应幂等，仅执行一次");

        log.info("[场景4] ✅ 重复分发幂等性验证通过");
    }
}
