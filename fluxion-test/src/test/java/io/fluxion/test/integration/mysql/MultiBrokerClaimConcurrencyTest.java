/*
 * Copyright 2025-2030 fluxion-io Team (https://github.com/Fluxion-io).
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

package io.fluxion.test.integration.mysql;

import io.fluxion.server.core.trigger.TriggerType;
import io.fluxion.server.core.broker.Broker;
import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.execution.Executable;
import io.fluxion.server.core.execution.ExecutableType;
import io.fluxion.server.core.execution.cmd.ExecutionsLoadCmd;
import io.fluxion.server.core.execution.cmd.ExecutionCreateCmd;
import io.fluxion.server.core.execution.service.ExecutionCommandService;
import io.fluxion.server.core.execution.service.ExecutionScheduleClaimBridge;
import io.fluxion.server.core.execution.service.ExecutionScheduleCommandService;
import io.fluxion.server.infrastructure.dao.entity.BucketEntity;
import io.fluxion.server.infrastructure.dao.entity.ExecutionEntity;
import io.fluxion.server.infrastructure.dao.repository.BucketEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.ExecutionEntityRepo;
import io.fluxion.server.infrastructure.schedule.task.DelayedTask;
import io.fluxion.server.infrastructure.schedule.scheduler.DelayedTaskScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 多 Broker 并发领取与租约接管验证（H2 MySQL 兼容模式，真实多线程/真实条件更新）。
 * <p>
 * 生产中同一执行点的跨 Broker 竞争窗口出现在 bucket 移交/租约过期瞬间；
 * {@code claim} 的条件 UPDATE 是唯一仲裁者。因此：
 * <ul>
 *   <li>T1 直击 claim 原语（桶移交窗口的竞争模拟，经同包桥接暴露）；</li>
 *   <li>T2 并发创建同一 (triggerId, triggerAt)，由数据库唯一键兜底幂等；</li>
 *   <li>T3 走完整生产入口 handle()：reclaim 过期租约 → bucket 移交 → 接管者重领。</li>
 * </ul>
 * 时间轮被 mock 截断在 claim 边界（记录回调不执行），聚焦并发原子性（R4 中可被 H2 表达的部分）。
 *
 * @author Devil
 */
@SpringBootTest(classes = MySqlTestApplication.class)
@ActiveProfiles("test-mysql")
@DisplayName("Multi-broker claim concurrency tests (H2 row-level)")
class MultiBrokerClaimConcurrencyTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private ExecutionScheduleCommandService executionScheduleCommandService;

    @Autowired
    private ExecutionCommandService executionCommandService;

    @Autowired
    private ExecutionEntityRepo executionEntityRepo;

    @Autowired
    private BucketEntityRepo bucketEntityRepo;

    private final List<DelayedTask> scheduledTasks = new ArrayList<>();

    @AfterEach
    void tearDown() {
        // BrokerContext 为静态单例，测试后置空避免泄漏到其他测试
        BrokerContext.initialize(null);
        scheduledTasks.clear();
    }

    @Test
    @DisplayName("T1: 两 Broker 并发 claim 同一 PENDING：条件 UPDATE 仅一个胜出")
    void shouldAllowOnlyOneWinnerWhenTwoBrokersClaimTheSamePendingRow() throws Exception {
        String executionId = seedPendingExecution(LocalDateTime.now().minusSeconds(1), 1);
        AtomicInteger winners = new AtomicInteger();

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> brokerA = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return ExecutionScheduleClaimBridge.claim(executionScheduleCommandService,
                    executionId, "broker-a-" + executionId, "token-a-" + executionId, false);
            };
            Callable<Boolean> brokerB = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return ExecutionScheduleClaimBridge.claim(executionScheduleCommandService,
                    executionId, "broker-b-" + executionId, "token-b-" + executionId, false);
            };
            List<Future<Boolean>> futures = new ArrayList<>();
            futures.add(pool.submit(brokerA));
            futures.add(pool.submit(brokerB));
            for (Future<Boolean> future : futures) {
                if (future.get(30, TimeUnit.SECONDS)) {
                    winners.incrementAndGet();
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(winners.get()).isEqualTo(1);
        ExecutionEntity after = executionEntityRepo.findById(executionId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo("claimed");
        // 胜者身份唯一：owner 与 token 一致地归属于两个候选之一
        assertThat(after.getLeaseOwner()).isIn("broker-a-" + executionId, "broker-b-" + executionId);
        assertThat(after.getExecutionToken()).isEqualTo(after.getLeaseOwner().replace("broker-", "token-"));
        assertThat(after.getLeaseUntil()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("T2: 两线程并发创建同一 (triggerId, triggerAt)：唯一键兜底，仅一个成功")
    void shouldCreateSingleExecutionForSameTriggerPointWhenCreatedConcurrently() throws Exception {
        String triggerId = "trigger-" + UUID.randomUUID();
        LocalDateTime triggerAt = LocalDateTime.now().plusMinutes(5);
        // T2 只触达创建路径：mock 的 Executable 仅提供 id/version/type 身份信息，其余方法不使用
        Executable executable = mock(Executable.class);
        when(executable.id()).thenReturn(triggerId);
        when(executable.version()).thenReturn("1");
        when(executable.type()).thenReturn(ExecutableType.EXECUTOR);

        AtomicInteger successes = new AtomicInteger();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 2; i++) {
                pool.submit(() -> {
                    try {
                        barrier.await(10, TimeUnit.SECONDS);
                        executionCommandService.handle(new ExecutionCreateCmd(
                            triggerId, TriggerType.SCHEDULE, executable, triggerAt));
                        successes.incrementAndGet();
                    } catch (org.springframework.dao.DataIntegrityViolationException e) {
                        // 唯一键 (trigger_id, trigger_at) 兜底：另一个并发创建被数据库拒绝
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        // 其他异常视为失败，交由成功计数断言暴露
                    }
                    return null;
                });
            }
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(successes.get()).isEqualTo(1);
        ExecutionEntity created = executionEntityRepo.findByTriggerIdAndTriggerAt(triggerId, triggerAt);
        assertThat(created).isNotNull();
        assertThat(created.getStatus()).isEqualTo("pending");
    }

    @Test
    @DisplayName("T3: 持有者租约过期：bucket 移交后存活 Broker 的 handle 先回收再重领，旧 token 作废")
    void shouldReclaimExpiredClaimAndReassignToSurvivingBrokerViaFullHandle() {
        String executionId = seedPendingExecution(LocalDateTime.now(), 1);

        // broker-a 通过完整 handle() 领取
        assertThat(handleAsBroker("broker-a")).isEqualTo(1);
        ExecutionEntity claimedByA = executionEntityRepo.findById(executionId).orElseThrow();
        String tokenA = claimedByA.getExecutionToken();
        assertThat(tokenA).isNotBlank();

        // 模拟 broker-a 死亡：租约拨过期 + BucketChecker 把 bucket 移交给 broker-b
        ExecutionEntity expired = executionEntityRepo.findById(executionId).orElseThrow();
        expired.setLeaseUntil(LocalDateTime.now().minusSeconds(1));
        executionEntityRepo.saveAndFlush(expired);
        handoverBucketTo("broker-b");

        // broker-b 的下一轮 load：reclaimExpiredClaims 先清 owner/token → 同轮内选取并重新领取
        assertThat(handleAsBroker("broker-b")).isEqualTo(1);

        ExecutionEntity after = executionEntityRepo.findById(executionId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo("claimed");
        assertThat(after.getLeaseOwner()).isEqualTo("broker-b");
        assertThat(after.getExecutionToken()).isNotEqualTo(tokenA);
        // 非 misfire 路径领取不增加 fire_attempt
        assertThat(after.getFireAttempt()).isZero();
    }

    // ==================== helpers ====================

    /** 以指定 broker 身份执行一轮完整 handle()；返回时间轮捕获的领取个数（即 claim 成功数） */
    private int handleAsBroker(String brokerId) {
        List<DelayedTask> captured = recordingBrokerContext(brokerId);
        executionScheduleCommandService.handle(new ExecutionsLoadCmd());
        return captured.size();
    }

    /** 初始化 mock BrokerContext；时间轮仅记录 DelayedTask 不执行，截断 Job 创建链 */
    private List<DelayedTask> recordingBrokerContext(String brokerId) {
        DelayedTaskScheduler scheduler = mock(DelayedTaskScheduler.class);
        List<DelayedTask> captured = new ArrayList<>();
        doAnswer(inv -> {
            captured.add(inv.getArgument(0));
            return null;
        }).when(scheduler).schedule(any(DelayedTask.class));
        Broker broker = mock(Broker.class);
        when(broker.id()).thenReturn(brokerId);
        when(broker.delayedTaskScheduler()).thenReturn(scheduler);
        BrokerContext.initialize(broker);
        seedBucketFor(brokerId);
        return captured;
    }

    /** bucket=1 建给指定 broker；移交时直接改写归属 */
    private void seedBucketFor(String brokerId) {
        bucketEntityRepo.findById(1).orElseGet(() -> {
            BucketEntity bucket = new BucketEntity();
            bucket.setBucket(1);
            bucket.setBrokerId(brokerId);
            return bucketEntityRepo.saveAndFlush(bucket);
        });
    }

    private void handoverBucketTo(String brokerId) {
        BucketEntity bucket = bucketEntityRepo.findById(1).orElseThrow();
        bucket.setBrokerId(brokerId);
        bucketEntityRepo.saveAndFlush(bucket);
    }

    private String seedPendingExecution(LocalDateTime triggerAt, int bucket) {
        ExecutionEntity entity = new ExecutionEntity();
        entity.setExecutionId("execution-" + UUID.randomUUID());
        entity.setTriggerId("trigger-" + UUID.randomUUID());
        entity.setTriggerType("schedule");
        entity.setExecutableId("executable-1");
        entity.setExecutableType("executor");
        entity.setExecutableVersion("1");
        entity.setStatus("pending");
        entity.setTriggerAt(triggerAt);
        entity.setBucket(bucket);
        entity.setFireAttempt(0);
        executionEntityRepo.saveAndFlush(entity);
        return entity.getExecutionId();
    }

}