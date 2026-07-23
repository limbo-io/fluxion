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

package io.fluxion.test.integration.mysql;

import io.fluxion.server.core.broker.Broker;
import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.infrastructure.lock.DatabaseDistributedLock;
import io.fluxion.server.infrastructure.lock.Locked;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MySQL-specific integration tests for DatabaseDistributedLock.
 * 
 * T4.2 Implementation Note:
 * Current implementation uses ThreadLocal tokens, allowing same-thread reacquisition
 * (testReentrantLock()). This is documented behavior but product review may change
 * to non-reentrant if business requirements differ.
 * <p>
 * Tests real MySQL behaviors that H2 cannot properly simulate:
 * - INSERT ... ON DUPLICATE KEY UPDATE for atomic lock acquisition
 * - Concurrent lock takeover with thread-safe token handling
 * - Lock expiration and renewal
 * - Two threads compete for lock → only one gets it
 * - Same-thread reentrant lock acquisition (current behavior)
 * 
 * These tests verify the MySQL-safe implementation of distributed locking
 * using UUID tokens and ThreadLocal for proper ownership tracking.
 */
@SpringBootTest(classes = MySqlTestApplication.class)
@ActiveProfiles("test-mysql")
@DisplayName("Distributed Lock MySQL Integration Tests")
class DistributedLockMySqlTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private DatabaseDistributedLock distributedLock;

    private static final String BROKER_A = "broker-a-" + UUID.randomUUID();
    private static final String BROKER_B = "broker-b-" + UUID.randomUUID();
    private static final String LOCK_NAME = "test-lock-" + UUID.randomUUID();
    private static final long LOCK_EXPIRE_MS = 5000; // 5 seconds for faster tests

    @BeforeEach
    void setUp() {
        // Set up a broker context for the test thread
        simulateBroker(BROKER_A);
    }

    @AfterEach
    void tearDown() {
        // Clean up any held locks
        try {
            distributedLock.unlock(LOCK_NAME);
        } catch (Exception ignored) {
            // May not hold the lock, that's fine
        }
    }

    @Test
    @DisplayName("T4.2: Try lock succeeds when lock is available")
    void testTryLockSuccess() {
        // When: Try to acquire lock
        boolean acquired = distributedLock.tryLock(LOCK_NAME, LOCK_EXPIRE_MS);

        // Then: Lock should be acquired
        assertThat(acquired).isTrue();

        // Note: Current implementation is REENTRANT (same thread can acquire multiple times)
        // This is achieved through ThreadLocal token storage
        // If non-reentrant behavior is required, the implementation needs to check
        // existing ownership before allowing re-acquisition
        boolean acquiredAgain = distributedLock.tryLock(LOCK_NAME, LOCK_EXPIRE_MS);
        assertThat(acquiredAgain).isTrue();
    }

    @Test
    @DisplayName("Try lock fails when another thread holds valid lock")
    void testTryLockFailureWhenHeld() throws InterruptedException {
        // Given: Thread 1 acquires the lock
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        AtomicInteger thread1HoldsLock = new AtomicInteger(0);
        
        Thread thread1 = new Thread(() -> {
            simulateBroker(BROKER_A);
            boolean acquired = distributedLock.tryLock(LOCK_NAME, LOCK_EXPIRE_MS);
            if (acquired) {
                thread1HoldsLock.set(1);
                latch1.countDown(); // Signal that we hold the lock
                try {
                    Thread.sleep(200); // Hold lock for 200ms
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                distributedLock.unlock(LOCK_NAME);
            }
        });

        AtomicInteger thread2Acquired = new AtomicInteger(0);
        Thread thread2 = new Thread(() -> {
            try {
                latch1.await(); // Wait for thread 1 to acquire
                simulateBroker(BROKER_B);
                // Try to acquire while thread 1 holds it
                boolean acquired = distributedLock.tryLock(LOCK_NAME, LOCK_EXPIRE_MS);
                thread2Acquired.set(acquired ? 1 : 0);
                if (acquired) {
                    distributedLock.unlock(LOCK_NAME);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            latch2.countDown();
        });

        thread1.start();
        thread2.start();

        // Wait for completion with timeout
        assertThat(latch2.await(5, TimeUnit.SECONDS)).isTrue();

        // Then: Thread 1 should have held the lock
        assertThat(thread1HoldsLock.get()).isEqualTo(1);
        // And: Thread 2 should have failed to acquire
        assertThat(thread2Acquired.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("Lock can be acquired after expiration")
    void testLockAcquisitionAfterExpiration() throws InterruptedException {
        // Given: A very short-lived lock
        String shortLockName = "short-lock-" + UUID.randomUUID();
        simulateBroker(BROKER_A);
        
        // Acquire with 100ms expiration
        boolean acquired = distributedLock.tryLock(shortLockName, 100);
        assertThat(acquired).isTrue();

        // When: Wait for expiration
        Thread.sleep(200);

        // Then: Another broker can acquire after expiration
        simulateBroker(BROKER_B);
        boolean acquiredByB = distributedLock.tryLock(shortLockName, LOCK_EXPIRE_MS);
        assertThat(acquiredByB).isTrue();

        // Cleanup
        distributedLock.unlock(shortLockName);
    }

    @Test
    @DisplayName("Only lock holder can unlock")
    void testOnlyHolderCanUnlock() {
        // Given: Broker A acquires lock
        simulateBroker(BROKER_A);
        Locked locked = distributedLock.tryAcquireLock(LOCK_NAME, LOCK_EXPIRE_MS);
        assertThat(locked).isNotNull();

        // When: Broker B tries to unlock (without holding)
        simulateBroker(BROKER_B);
        boolean unlockedByB = distributedLock.unlock(LOCK_NAME);

        // Then: Unlock should fail
        assertThat(unlockedByB).isFalse();

        // When: Original holder unlocks with correct token
        boolean unlockedByA = distributedLock.unlock(locked);

        // Then: Unlock should succeed
        assertThat(unlockedByA).isTrue();
    }

    @Test
    @DisplayName("Lock with wait retries until timeout")
    void testLockWithWaitTimeout() {
        // Given: Lock is held by another thread
        CountDownLatch holdLatch = new CountDownLatch(1);
        
        Thread holder = new Thread(() -> {
            simulateBroker(BROKER_A);
            distributedLock.tryLock(LOCK_NAME, 5000); // Hold for 5 seconds
            holdLatch.countDown();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            distributedLock.unlock(LOCK_NAME);
        });
        holder.start();

        try {
            holdLatch.await(); // Wait for holder to acquire

            // When: Try to get lock with 100ms wait timeout
            simulateBroker(BROKER_B);
            long startTime = System.currentTimeMillis();
            
            boolean acquired = false;
            try {
                distributedLock.lock(LOCK_NAME, LOCK_EXPIRE_MS, 100, () -> "value");
                acquired = true;
            } catch (Exception e) {
                // Expected to timeout
            }
            long elapsed = System.currentTimeMillis() - startTime;

            // Then: Should have waited approximately 100ms and failed
            assertThat(acquired).isFalse();
            assertThat(elapsed).isGreaterThanOrEqualTo(100);
            assertThat(elapsed).isLessThan(500); // Reasonable upper bound

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("Concurrent lock acquisition - only one thread gets lock")
    void testConcurrentAcquisition() throws InterruptedException {
        // Given: Multiple threads competing for the same lock
        int threadCount = 10;
        String lockName = "concurrent-lock-" + UUID.randomUUID();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // All threads start simultaneously
                    
                    // Each thread simulates a different broker
                    String brokerId = "broker-" + threadIndex + "-" + UUID.randomUUID();
                    simulateBroker(brokerId);
                    
                    boolean acquired = distributedLock.tryLock(lockName, 10000);
                    if (acquired) {
                        successCount.incrementAndGet();
                        // Hold lock briefly
                        Thread.sleep(50);
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
        executor.shutdown();

        // Then: Only one thread should have acquired the lock
        assertThat(successCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("T4.2: Lock is reentrant for same thread (current implementation)")
    void testReentrantLock() {
        // T4.2: Document current reentrant behavior
        // The lock uses ThreadLocal to store tokens, allowing same-thread reacquisition
        // If product requires non-reentrant locks, implementation needs modification

        // When: Acquire lock multiple times (reentrant)
        boolean first = distributedLock.tryLock(LOCK_NAME, LOCK_EXPIRE_MS);
        boolean second = distributedLock.tryLock(LOCK_NAME, LOCK_EXPIRE_MS);
        boolean third = distributedLock.tryLock(LOCK_NAME, LOCK_EXPIRE_MS);

        // Then: All acquisitions succeed (reentrant behavior)
        assertThat(first).isTrue();
        assertThat(second).isTrue();
        assertThat(third).isTrue();

        // When: Unlock (clears ThreadLocal token)
        distributedLock.unlock(LOCK_NAME);

        // Then: Another broker can acquire after unlock
        simulateBroker(BROKER_B);
        boolean acquiredByB = distributedLock.tryLock(LOCK_NAME, LOCK_EXPIRE_MS);
        assertThat(acquiredByB).isTrue();
        distributedLock.unlock(LOCK_NAME);
    }

    private void simulateBroker(String brokerId) {
        Broker mockBroker = mock(Broker.class);
        when(mockBroker.id()).thenReturn(brokerId);
        BrokerContext.initialize(mockBroker);
    }
}
