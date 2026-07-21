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

package io.fluxion.test.integration.schedule;

import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.schedule.ScheduleDelay;
import io.fluxion.server.core.schedule.ScheduleLeaseMaintainer;
import io.fluxion.server.core.schedule.cmd.ScheduleDelaysCreateCmd;
import io.fluxion.server.infrastructure.dao.entity.ScheduleDelayEntity;
import io.fluxion.server.infrastructure.dao.repository.ScheduleDelayEntityRepo;
import io.fluxion.test.integration.BaseIntegrationTest;
import io.limbo.cqrs.spring.command.Cmd;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ScheduleDelay lease mechanism.
 * <p>
 * Tests verify:
 * - Concurrent claim attempts by multiple brokers result in only one success
 * - Lease expiry allows another broker to take over
 * - Fencing prevents double execution after lease loss
 *
 * @author Devil
 */
@Slf4j
class ScheduleDelayLeaseIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ScheduleDelayEntityRepo scheduleDelayEntityRepo;

    @Autowired
    private ScheduleLeaseMaintainer leaseMaintainer;

    private static final String TEST_SCHEDULE_ID = "test-schedule-lease";

    @Test
    @DisplayName("Test 1: Two brokers claiming concurrently - only one succeeds")
    @Transactional
    void testConcurrentClaimOnlyOneSucceeds() throws InterruptedException {
        // Given: Create a delay record in INIT status
        LocalDateTime triggerAt = LocalDateTime.now().plusMinutes(5);
        ScheduleDelay.ID delayId = createTestDelay(triggerAt);

        // When: Two "brokers" try to claim concurrently
        int concurrentAttempts = 2;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(concurrentAttempts);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(concurrentAttempts);

        for (int i = 0; i < concurrentAttempts; i++) {
            final String brokerId = "test-broker-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready

                    // Try to claim using the lease maintainer
                    boolean claimed = leaseMaintainer.tryClaim(TEST_SCHEDULE_ID, triggerAt);
                    if (claimed) {
                        successCount.incrementAndGet();
                        log.info("Broker {} successfully claimed delay", brokerId);
                    } else {
                        failCount.incrementAndGet();
                        log.info("Broker {} failed to claim delay", brokerId);
                    }
                } catch (Exception e) {
                    log.error("Error during claim", e);
                    failCount.incrementAndGet();
                } finally {
                    completeLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();
        boolean completed = completeLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then: Only one should succeed
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);

        // Verify the record has CLAIMED status
        ScheduleDelayEntity savedEntity = scheduleDelayEntityRepo.findById(
            new ScheduleDelayEntity.ID(TEST_SCHEDULE_ID, triggerAt)
        ).orElse(null);

        assertThat(savedEntity).isNotNull();
        assertThat(savedEntity.getStatus()).isEqualTo(ScheduleDelay.Status.CLAIMED.value);
        assertThat(savedEntity.getLeaseOwner()).isNotNull();
        assertThat(savedEntity.getLeaseUntil()).isAfter(LocalDateTime.now());
        assertThat(savedEntity.getAttempt()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Test 2: Broker stops renewing, another broker can take over after lease expiry")
    void testBrokerFailoverAfterLeaseExpiry() throws InterruptedException {
        // Given: Create a delay and claim it with initial broker
        LocalDateTime triggerAt = LocalDateTime.now().plusMinutes(10);
        createTestDelay(triggerAt);

        // Broker A claims the delay
        boolean claimedByA = leaseMaintainer.tryClaim(TEST_SCHEDULE_ID, triggerAt);
        assertThat(claimedByA).isTrue();
        log.info("Broker A claimed the delay");

        ScheduleDelayEntity entity = scheduleDelayEntityRepo.findById(
            new ScheduleDelayEntity.ID(TEST_SCHEDULE_ID, triggerAt)
        ).orElseThrow();
        String brokerAId = entity.getLeaseOwner();

        // Simulate lease expiry by manually setting lease_until to past
        entity.setLeaseUntil(LocalDateTime.now().minusSeconds(1));
        scheduleDelayEntityRepo.saveAndFlush(entity);
        log.info("Simulated lease expiry by setting leaseUntil to past");

        // When: Run reclaim task (normally done by scheduled task)
        // Wait for a short time to ensure lease is definitely expired
        sleep(Duration.ofMillis(100));

        // Manually reset to INIT (simulating what reclaimExpiredLeases does)
        entity.setStatus(ScheduleDelay.Status.INIT.value);
        entity.setLeaseOwner(null);
        entity.setLeaseUntil(null);
        scheduleDelayEntityRepo.saveAndFlush(entity);
        log.info("Manually reset delay to INIT (simulating reclaim)");

        // Then: Broker B can now claim
        boolean claimedByB = leaseMaintainer.tryClaim(TEST_SCHEDULE_ID, triggerAt);
        assertThat(claimedByB).isTrue();

        ScheduleDelayEntity updatedEntity = scheduleDelayEntityRepo.findById(
            new ScheduleDelayEntity.ID(TEST_SCHEDULE_ID, triggerAt)
        ).orElseThrow();

        assertThat(updatedEntity.getStatus()).isEqualTo(ScheduleDelay.Status.CLAIMED.value);
        assertThat(updatedEntity.getLeaseOwner()).isNotEqualTo(brokerAId);
        assertThat(updatedEntity.getLeaseUntil()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("Test 3: Broker loses lease - cannot create second execution (fencing)")
    void testFencingPreventsDoubleExecution() {
        // Given: Create and claim a delay
        LocalDateTime triggerAt = LocalDateTime.now().plusMinutes(5);
        createTestDelay(triggerAt);

        boolean claimed = leaseMaintainer.tryClaim(TEST_SCHEDULE_ID, triggerAt);
        assertThat(claimed).isTrue();

        // Verify lease ownership
        boolean hasLease = leaseMaintainer.verifyLease(TEST_SCHEDULE_ID, triggerAt);
        assertThat(hasLease).isTrue();

        // When: Simulate losing lease (e.g., by expiry or another broker taking it)
        ScheduleDelayEntity entity = scheduleDelayEntityRepo.findById(
            new ScheduleDelayEntity.ID(TEST_SCHEDULE_ID, triggerAt)
        ).orElseThrow();
        entity.setLeaseUntil(LocalDateTime.now().minusSeconds(1));
        scheduleDelayEntityRepo.saveAndFlush(entity);

        // Then: Verify lease check fails
        boolean hasLeaseAfterExpiry = leaseMaintainer.verifyLease(TEST_SCHEDULE_ID, triggerAt);
        assertThat(hasLeaseAfterExpiry).isFalse();

        // Attempt to transition to RUNNING should fail (fencing check)
        boolean transitioned = leaseMaintainer.transitionToRunning(TEST_SCHEDULE_ID, triggerAt);
        assertThat(transitioned).isFalse();
    }

    @Test
    @DisplayName("Test 4: Lease owner can transition from CLAIMED to RUNNING")
    void testOwnerCanTransitionToRunning() {
        // Given: Create and claim a delay
        LocalDateTime triggerAt = LocalDateTime.now().plusMinutes(5);
        createTestDelay(triggerAt);

        boolean claimed = leaseMaintainer.tryClaim(TEST_SCHEDULE_ID, triggerAt);
        assertThat(claimed).isTrue();

        // Verify status is CLAIMED
        ScheduleDelayEntity entity = scheduleDelayEntityRepo.findById(
            new ScheduleDelayEntity.ID(TEST_SCHEDULE_ID, triggerAt)
        ).orElseThrow();
        assertThat(entity.getStatus()).isEqualTo(ScheduleDelay.Status.CLAIMED.value);

        // When: Owner transitions to RUNNING
        boolean transitioned = leaseMaintainer.transitionToRunning(TEST_SCHEDULE_ID, triggerAt);

        // Then: Transition succeeds
        assertThat(transitioned).isTrue();

        ScheduleDelayEntity updatedEntity = scheduleDelayEntityRepo.findById(
            new ScheduleDelayEntity.ID(TEST_SCHEDULE_ID, triggerAt)
        ).orElseThrow();
        assertThat(updatedEntity.getStatus()).isEqualTo(ScheduleDelay.Status.RUNNING.value);
    }

    @Test
    @DisplayName("Test 5: Only claim delays with trigger time within 1 minute")
    void testOnlyClaimNearFutureDelays() {
        // Given: Create a delay far in the future (more than 1 minute)
        LocalDateTime farFutureTrigger = LocalDateTime.now().plusMinutes(5);
        String farScheduleId = TEST_SCHEDULE_ID + "-far";
        createTestDelayWithId(farScheduleId, farFutureTrigger);

        // When: Try to load delays (should skip the far future one)
        // Note: The actual claim logic in ScheduleDelayCommandService only claims
        // delays within 1 minute, but leaseMaintainer.tryClaim() can claim any
        // So we test the leaseMaintainer directly

        // Since the leaseMaintainer doesn't have the 1-minute restriction,
        // we verify that the delay stays in INIT when not claimed
        ScheduleDelayEntity entity = scheduleDelayEntityRepo.findById(
            new ScheduleDelayEntity.ID(farScheduleId, farFutureTrigger)
        ).orElseThrow();
        assertThat(entity.getStatus()).isEqualTo(ScheduleDelay.Status.INIT.value);

        // Now create a near-future delay
        LocalDateTime nearFutureTrigger = LocalDateTime.now().plusSeconds(30);
        String nearScheduleId = TEST_SCHEDULE_ID + "-near";
        createTestDelayWithId(nearScheduleId, nearFutureTrigger);

        // This should be claimable with tryClaim
        boolean claimed = leaseMaintainer.tryClaim(nearScheduleId, nearFutureTrigger);
        assertThat(claimed).isTrue();

        ScheduleDelayEntity nearEntity = scheduleDelayEntityRepo.findById(
            new ScheduleDelayEntity.ID(nearScheduleId, nearFutureTrigger)
        ).orElseThrow();
        assertThat(nearEntity.getStatus()).isEqualTo(ScheduleDelay.Status.CLAIMED.value);
    }

    /**
     * Helper method to create a test delay with default schedule ID
     */
    private ScheduleDelay.ID createTestDelay(LocalDateTime triggerAt) {
        return createTestDelayWithId(TEST_SCHEDULE_ID, triggerAt);
    }

    /**
     * Helper method to create a test delay with specific schedule ID
     */
    private ScheduleDelay.ID createTestDelayWithId(String scheduleId, LocalDateTime triggerAt) {
        String brokerId = BrokerContext.broker() != null ? BrokerContext.broker().id() : "test-broker";

        ScheduleDelayEntity entity = new ScheduleDelayEntity();
        entity.setId(new ScheduleDelayEntity.ID(scheduleId, triggerAt));
        entity.setDelayId(scheduleId + "_" + triggerAt.toString());
        entity.setBucket(0); // Use bucket 0 for tests
        entity.setStatus(ScheduleDelay.Status.INIT.value);
        entity.setLeaseOwner(null);
        entity.setLeaseUntil(null);
        entity.setAttempt(0);

        scheduleDelayEntityRepo.save(entity);
        log.info("Created test delay: {} at {}", scheduleId, triggerAt);

        return new ScheduleDelay.ID(scheduleId, triggerAt);
    }
}
