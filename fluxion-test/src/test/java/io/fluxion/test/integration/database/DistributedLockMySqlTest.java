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

package io.fluxion.test.integration.database;

import io.fluxion.server.core.broker.Broker;
import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.infrastructure.dao.repository.LockEntityRepo;
import io.fluxion.server.infrastructure.lock.DatabaseDistributedLock;
import io.fluxion.server.infrastructure.lock.Locked;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MySQL-specific integration tests for DatabaseDistributedLock.
 * 
 * T4.2 Implementation Note:
 * The lock is NON-reentrant: the "reentrant" behavior previously documented was an
 * artifact of Connector/J's CLIENT_FOUND_ROWS default (INSERT ... ON DUPLICATE
 * UPDATE with unchanged values reported 1 instead of 0). With useAffectedRows=true
 * the database predicate "cannot overwrite before expire_at" is honored, so even
 * the holding thread is rejected while the lock is unexpired. There is no product
 * requirement for reentrancy (see findings: reentrancy expectation had no product
 * basis); callers that need reentrancy should implement their own local counting.
 * <p>
 * Tests real MySQL behaviors that H2 cannot properly simulate:
 * - INSERT ... ON DUPLICATE KEY UPDATE for atomic lock acquisition
 * - Concurrent lock takeover with thread-safe token handling
 * - Lock expiration and renewal
 * - Two threads compete for lock → only one gets it
 * - Same-thread reacquisition is rejected while held (non-reentrant)
 * 
 * These tests verify the MySQL-safe implementation of distributed locking
 * using UUID tokens and ThreadLocal for proper ownership tracking.
 */
@SpringBootTest(classes = {MySqlTestApplication.class, DistributedLockMySqlTest.LockConfig.class})
@ActiveProfiles("test-real-mysql")
@DisplayName("Distributed Lock MySQL Integration Tests")
class DistributedLockMySqlTest extends AbstractMySqlIntegrationTest {

    /**
     * 真实 MySQL 专用：MySqlTestApplication 的扫描排除了 DatabaseDistributedLock（H2 路径用不到），
     * 此处对 real-mysql 上下文显式注册回来。
     */
    @Configuration(proxyBeanMethods = false)
    @Import(DatabaseDistributedLock.class)
    static class LockConfig {
    }

    @Autowired
    private DatabaseDistributedLock distributedLock;

    @Autowired
    private LockEntityRepo lockEntityRepo;

    private static final String BROKER_A = "broker-a-" + UUID.randomUUID();
    private static final String BROKER_B = "broker-b-" + UUID.randomUUID();
    /** 泛用锁过期窗；过期接管类用例单独用更紧的窗口 */
    private static final long LOCK_EXPIRE_MS = 5000;

    /**
     * 每个用例用独立锁名：锁的行为是跨线程竞态，而 ThreadLocal 凭据、后台持有线程的生命周期
     * 都可能把未到期行遗留到后续用例；唯一锁名把用例间状态彻底隔断（hermetic）。
     * 双时钟容忍：expire_at 由宿主 JVM 时钟写入，谓词 NOW(3) 由 MySQL 服务器时钟求值，
     * 虚机/容器漂移下过期接管窗口必须留出秒级余量。
     */
    private static String freshLockName(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    @BeforeEach
    void setUp() {
        // Set up a broker context for the test thread
        simulateBroker(BROKER_A);
    }

    @AfterEach
    void tearDown() {
        // 每个用例在自身内释放/让行过期：锁名唯一后无类级共享残留需要清理
    }

    @Test
    @DisplayName("T4.2: Try lock succeeds when lock is available")
    void testTryLockSuccess() {
        String lockName = freshLockName("try-lock");
        // When: Try to acquire lock
        boolean acquired = distributedLock.tryLock(lockName, LOCK_EXPIRE_MS);

        // Then: Lock should be acquired
        assertThat(acquired).isTrue();

        // 锁语义为非重入：真实 affected-rows 语义下（useAffectedRows=true），
        // 持有期间同线程再 acquire 会被“未过期不可覆盖”谓词拒绝。
        // （旧“可重入”是 CLIENT_FOUND_ROWS 恒真 bug 的产物，见审计记录）
        boolean acquiredAgain = distributedLock.tryLock(lockName, LOCK_EXPIRE_MS);
        assertThat(acquiredAgain).isFalse();
    }

    @Test
    @DisplayName("Try lock fails when another thread holds valid lock")
    void testTryLockFailureWhenHeld() throws InterruptedException {
        String lockName = freshLockName("held-lock");
        // 三步定序：T1 获取→T2 尝试→T1 释放。持有期间隔由 latch 驱动而非固定 sleep，
        // 消除“T2 调度延迟到 T1 过期后”的竞态窗口；所有 await 均有界防 CI 挂死。
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch attempted = new CountDownLatch(1);
        CountDownLatch released = new CountDownLatch(1);
        AtomicInteger thread1HoldsLock = new AtomicInteger(0);

        Thread thread1 = new Thread(() -> {
            try {
                simulateBroker(BROKER_A);
                boolean acquired = distributedLock.tryLock(lockName, LOCK_EXPIRE_MS);
                if (acquired) {
                    thread1HoldsLock.set(1);
                    held.countDown(); // Signal that we hold the lock
                    // 等 T2 在持有期间完成其尝试后再释放（事件驱动，不靠墙钟窗口）
                    if (!attempted.await(10, TimeUnit.SECONDS)) {
                        return;
                    }
                    distributedLock.unlock(lockName);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                released.countDown();
            }
        });

        AtomicInteger thread2Acquired = new AtomicInteger(-1);
        Thread thread2 = new Thread(() -> {
            try {
                if (!held.await(10, TimeUnit.SECONDS)) {
                    return;
                }
                simulateBroker(BROKER_B);
                // Try to acquire while thread 1 holds it
                boolean acquired = distributedLock.tryLock(lockName, LOCK_EXPIRE_MS);
                thread2Acquired.set(acquired ? 1 : 0);
                if (acquired) {
                    distributedLock.unlock(lockName);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                attempted.countDown();
            }
        });

        thread1.start();
        thread2.start();

        // Wait for completion with timeout
        assertThat(attempted.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(released.await(10, TimeUnit.SECONDS)).isTrue();

        // Then: Thread 1 should have held the lock
        assertThat(thread1HoldsLock.get()).isEqualTo(1);
        // And: Thread 2 should have failed to acquire（-1 表示 T2 未在窗口内完成，同样视为失败）
        assertThat(thread2Acquired.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("Lock can be acquired after expiration")
    void testLockAcquisitionAfterExpiration() throws InterruptedException {
        // 双时钟容忍：expire_at 由宿主 JVM 时钟写入，谓词 NOW(3) 由 MySQL 会话时钟
        // （init-sql 固定 UTC）求值；虚机时钟漂移与负载尖峰下用轮询等待“过期接管”发生。
        String shortLockName = freshLockName("short-lock");
        simulateBroker(BROKER_A);

        // Acquire with 1000ms expiration
        boolean acquired = distributedLock.tryLock(shortLockName, 1000);
        assertThat(acquired).isTrue();

        // When: 轮询等待过期接管发生（窗口 1s + 4s 漂移余量，每次间隔 100ms）
        simulateBroker(BROKER_B);
        boolean acquiredByB = false;
        long deadline = System.currentTimeMillis() + 5_000;
        while (!acquiredByB && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
            acquiredByB = distributedLock.tryLock(shortLockName, LOCK_EXPIRE_MS);
        }

        // Then: Another broker can acquire after expiration
        assertThat(acquiredByB).isTrue();

        // Cleanup
        distributedLock.unlock(shortLockName);
    }

    @Test
    @DisplayName("Only lock holder can unlock")
    void testOnlyHolderCanUnlock() throws InterruptedException {
        String lockName = freshLockName("holder-lock");
        // 实现所有权凭据 = 持有线程的 ThreadLocal token + DB 行 owner 双重匹配（见
        // DatabaseDistributedLock javadoc）；broker 身份不参与。因此“非持有者”必须是另一个线程。
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch verifyDone = new CountDownLatch(1);
        AtomicReference<Locked> lockedRef = new AtomicReference<>();
        AtomicBoolean unlockedByHolder = new AtomicBoolean(false);

        Thread holder = new Thread(() -> {
            simulateBroker(BROKER_A);
            Locked locked = distributedLock.tryAcquireLock(lockName, LOCK_EXPIRE_MS);
            lockedRef.set(locked);
            acquired.countDown();
            try {
                // 等主线程完成“非持有者拒绝”断言后，由持有线程自己解锁
                if (!verifyDone.await(5, TimeUnit.SECONDS)) {
                    return;
                }
                unlockedByHolder.set(distributedLock.unlock(locked));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "lock-holder-a");
        holder.start();
        assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(lockedRef.get()).isNotNull();

        // When: 非持有线程（无令牌）尝试解锁——无论凭 name 还是凭句柄
        // Then: 均被拒绝，锁行保持持有状态
        assertThat(distributedLock.unlock(lockName)).isFalse();
        assertThat(distributedLock.unlock(lockedRef.get())).isFalse();
        assertThat(lockEntityRepo.findByName(lockName)).isNotNull();

        verifyDone.countDown();
        holder.join(5000);
        // Then: 持有线程以正确句柄解锁成功
        assertThat(unlockedByHolder.get()).isTrue();
        assertThat(lockEntityRepo.findByName(lockName)).isNull();
    }

    @Test
    @DisplayName("Lock with wait retries until timeout")
    void testLockWithWaitTimeout() throws InterruptedException {
        String lockName = freshLockName("wait-lock");
        // Given: Lock is held by another thread。持有期间隔由 latch 驱动（B 完成尝试即释放），
        // 不依赖固定 sleep 窗口；所有 await 均有界。
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch attemptDone = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            try {
                simulateBroker(BROKER_A);
                assertThat(distributedLock.tryLock(lockName, LOCK_EXPIRE_MS)).isTrue();
                held.countDown();
                // 等 B 完成 wait=100ms 的尝试后释放（事件驱动持有窗口）
                if (!attemptDone.await(15, TimeUnit.SECONDS)) {
                    // 超时兜底：不无限等，剩余持有交由过期接管
                }
                distributedLock.unlock(lockName);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        holder.start();

        try {
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();

            // When: Try to get lock with 100ms wait timeout
            simulateBroker(BROKER_B);
            long startTime = System.currentTimeMillis();

            boolean acquired = false;
            try {
                distributedLock.lock(lockName, LOCK_EXPIRE_MS, 100, () -> "value");
                acquired = true;
            } catch (Exception e) {
                // Expected to timeout
            } finally {
                attemptDone.countDown();
            }
            long elapsed = System.currentTimeMillis() - startTime;

            // Then: Should have failed within bounded time（wait 参数语义），
            // 而不是等到持锁线程释放——上限取显著小于持有窗口的宽松值，容忍 CI 抖动
            assertThat(acquired).isFalse();
            assertThat(elapsed).isLessThan(15_000);

        } finally {
            // 等持有线程释放后再离开：跳过释放会遗留未到期行（虽锁名唯一不影响后续用例，
            // 但避免后台线程跨用例存活）
            try {
                holder.join(15_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    @DisplayName("Concurrent lock acquisition - only one thread gets lock")
    void testConcurrentAcquisition() throws InterruptedException {
        // Given: Multiple threads competing for the same lock
        int threadCount = 10;
        String lockName = freshLockName("concurrent-lock");
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    if (!startLatch.await(30, TimeUnit.SECONDS)) {
                        return;
                    }

                    // Each thread simulates a different broker
                    String brokerId = "broker-" + threadIndex + "-" + UUID.randomUUID();
                    simulateBroker(brokerId);

                    boolean acquired = distributedLock.tryLock(lockName, LOCK_EXPIRE_MS);
                    if (acquired) {
                        successCount.incrementAndGet();
                        distributedLock.unlock(lockName);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completeLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for completion
        assertThat(completeLatch.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(executor.shutdownNow().isEmpty()).isTrue();

        // Then: Only one thread should have acquired the lock
        assertThat(successCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("T4.2: Lock is non-reentrant even for the holding thread")
    void testReentrantLock() {
        String lockName = freshLockName("reentrant-lock");
        // 锁语义为非重入（无产品依据要求可重入，见 findings 删除候选表）。
        // ThreadLocal 只保存解锁凭据，不赋予再次持有的权利：同一持有线程在未过期前
        // 再次 tryLock，仍被数据库“未过期不可覆盖”谓词拒绝。

        // When: Acquire then reacquire on the same thread
        boolean first = distributedLock.tryLock(lockName, LOCK_EXPIRE_MS);
        boolean second = distributedLock.tryLock(lockName, LOCK_EXPIRE_MS);

        // Then: Only the first acquisition succeeds
        assertThat(first).isTrue();
        assertThat(second).isFalse();

        // When: Unlock (clears ThreadLocal token)
        assertThat(distributedLock.unlock(lockName)).isTrue();

        // Then: Another broker can acquire after unlock
        simulateBroker(BROKER_B);
        boolean acquiredByB = distributedLock.tryLock(lockName, LOCK_EXPIRE_MS);
        assertThat(acquiredByB).isTrue();
        distributedLock.unlock(lockName);
    }

    private void simulateBroker(String brokerId) {
        Broker mockBroker = mock(Broker.class);
        when(mockBroker.id()).thenReturn(brokerId);
        BrokerContext.initialize(mockBroker);
    }
}
