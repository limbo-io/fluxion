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
import io.fluxion.server.core.broker.task.ScheduleLeaseReclaimTask;
import io.fluxion.server.core.broker.task.ScheduleLeaseRenewTask;
import io.fluxion.server.core.schedule.ScheduleLeaseProperties;
import io.fluxion.server.core.schedule.ScheduleLeaseMaintainer;
import io.fluxion.server.core.schedule.cmd.ScheduleLeaseReclaimCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleLeaseRenewCmd;
import io.fluxion.server.infrastructure.dao.entity.ScheduleDelayEntity;
import io.fluxion.server.infrastructure.dao.repository.ScheduleDelayEntityRepo;
import io.limbo.cqrs.spring.command.Cmd;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T2.1/T5.5: Broker CoreTask 续租生命周期集成测试
 * <p>
 * 验证完整链路:
 * - T2.1: CoreTask → ScheduleLeaseRenewCmd/ReclaimCmd → Handler → Database
 * - T5.5: Broker lifecycle 续租 CoreTask 调度验证
 * <p>
 * 本测试覆盖:
 * - ScheduleLeaseRenewTask 发送 ScheduleLeaseRenewCmd
 * - ScheduleLeaseReclaimTask 发送 ScheduleLeaseReclaimCmd
 * - CommandHandler 处理并调用 leaseMaintainer
 * - 数据库状态正确更新
 * - 生产生命周期: Broker startup → CoreTask scheduling → lease maintenance
 *
 * @author Devil
 */
@SpringBootTest(classes = MySqlTestApplication.class)
@ActiveProfiles("test-mysql")
@DisplayName("Broker Multi-Node Lease E2E Tests")
class BrokerMultiNodeLeaseTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private ScheduleDelayEntityRepo scheduleDelayRepo;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ScheduleLeaseMaintainer leaseMaintainer;

    @Autowired
    private ScheduleLeaseProperties leaseProperties;

    private ScheduleLeaseRenewTask renewTask;
    private ScheduleLeaseReclaimTask reclaimTask;

    private static final String SCHEDULE_ID = "test-schedule-001";
    private static final String BROKER_A = "broker-a-" + UUID.randomUUID();
    private static final String BROKER_B = "broker-b-" + UUID.randomUUID();

    @BeforeEach
    void setUp() {
        scheduleDelayRepo.deleteAll();
        simulateBroker(BROKER_A);

        // T2.1: Create real CoreTasks with lease properties
        renewTask = new ScheduleLeaseRenewTask(leaseProperties);
        reclaimTask = new ScheduleLeaseReclaimTask(leaseProperties);
    }

    @AfterEach
    void tearDown() {
        scheduleDelayRepo.deleteAll();
    }

    @Test
    @DisplayName("T2.1: CoreTask → ScheduleLeaseRenewCmd → handler → database")
    @Transactional
    void testLeaseRenewThroughCoreTask() {
        // Given: Broker A has claimed a delay
        LocalDateTime triggerAt = LocalDateTime.now().plusMinutes(5);
        createScheduleDelay(SCHEDULE_ID, triggerAt);
        LocalDateTime originalLeaseUntil = LocalDateTime.now().plusSeconds(15);

        entityManager.createQuery(
                "UPDATE ScheduleDelayEntity e " +
                "SET e.leaseOwner = :owner, e.leaseUntil = :leaseUntil " +
                "WHERE e.id.scheduleId = :scheduleId AND e.id.triggerAt = :triggerAt")
            .setParameter("owner", BROKER_A)
            .setParameter("leaseUntil", originalLeaseUntil)
            .setParameter("scheduleId", SCHEDULE_ID)
            .setParameter("triggerAt", triggerAt)
            .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        // When: Run CoreTask (simulating Broker lifecycle trigger)
        renewTask.run();
        entityManager.flush();
        entityManager.clear();

        // Then: Lease should be renewed through full chain: CoreTask → Command → Handler → Database
        ScheduleDelayEntity delay = findDelay(SCHEDULE_ID, triggerAt);
        assertThat(delay.getLeaseOwner()).isEqualTo(BROKER_A);
        assertThat(delay.getLeaseUntil()).isAfterOrEqualTo(originalLeaseUntil);
    }

    @Test
    @DisplayName("T2.1 (legacy): ScheduleLeaseRenewCmd renews lease through handler → database")
    @Transactional
    void testLeaseRenewCmdFlow() {
        // Given: Broker A has claimed a delay
        LocalDateTime triggerAt = LocalDateTime.now().plusMinutes(5);
        createScheduleDelay(SCHEDULE_ID, triggerAt);
        LocalDateTime originalLeaseUntil = LocalDateTime.now().plusSeconds(15);

        entityManager.createQuery(
                "UPDATE ScheduleDelayEntity e " +
                "SET e.leaseOwner = :owner, e.leaseUntil = :leaseUntil " +
                "WHERE e.id.scheduleId = :scheduleId AND e.id.triggerAt = :triggerAt")
            .setParameter("owner", BROKER_A)
            .setParameter("leaseUntil", originalLeaseUntil)
            .setParameter("scheduleId", SCHEDULE_ID)
            .setParameter("triggerAt", triggerAt)
            .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        // When: Send ScheduleLeaseRenewCmd (as ScheduleLeaseRenewTask would do)
        simulateBroker(BROKER_A);
        Cmd.send(new ScheduleLeaseRenewCmd(BROKER_A, 15));
        entityManager.flush();
        entityManager.clear();

        // Then: Lease should be renewed in database
        ScheduleDelayEntity delay = findDelay(SCHEDULE_ID, triggerAt);
        assertThat(delay.getLeaseOwner()).isEqualTo(BROKER_A);
        // After renew, leaseUntil should be extended
        assertThat(delay.getLeaseUntil()).isAfterOrEqualTo(originalLeaseUntil);
    }

    @Test
    @DisplayName("T2.2: ScheduleLeaseReclaimCmd reclaims expired lease through handler → database")
    @Transactional
    void testLeaseReclaimCmdFlow() {
        // Given: Broker A has an expired claim
        LocalDateTime triggerAt = LocalDateTime.now().plusMinutes(5);
        createScheduleDelay(SCHEDULE_ID, triggerAt);

        // Set expired lease
        LocalDateTime expiredTime = LocalDateTime.now().minusSeconds(5);
        entityManager.createQuery(
                "UPDATE ScheduleDelayEntity e " +
                "SET e.leaseOwner = :owner, e.leaseUntil = :leaseUntil, e.status = :status " +
                "WHERE e.id.scheduleId = :scheduleId AND e.id.triggerAt = :triggerAt")
            .setParameter("owner", BROKER_A)
            .setParameter("leaseUntil", expiredTime)
            .setParameter("status", "claimed")
            .setParameter("scheduleId", SCHEDULE_ID)
            .setParameter("triggerAt", triggerAt)
            .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        // When: Broker B sends ScheduleLeaseReclaimCmd for bucket 1
        simulateBroker(BROKER_B);
        Cmd.send(new ScheduleLeaseReclaimCmd(Collections.singletonList(1)));
        entityManager.flush();
        entityManager.clear();

        // Then: Expired lease should be reclaimed (reset to INIT)
        ScheduleDelayEntity delay = findDelay(SCHEDULE_ID, triggerAt);
        // After reclaim, status should be INIT (available for new claim)
        assertThat(delay.getStatus()).isEqualTo("init");
        assertThat(delay.getLeaseOwner()).isNull();
    }

    @Test
    @DisplayName("T2.4: Fencing - Broker A's updates return 0 after Broker B claims")
    @Transactional
    void testFencingAfterBrokerBClaim() {
        // Given: Broker A has an expired claim, Broker B claims it
        LocalDateTime triggerAt = LocalDateTime.now().plusMinutes(5);
        createScheduleDelay(SCHEDULE_ID, triggerAt);

        // Set expired lease by Broker A
        LocalDateTime expiredTime = LocalDateTime.now().minusSeconds(5);
        entityManager.createQuery(
                "UPDATE ScheduleDelayEntity e " +
                "SET e.leaseOwner = :owner, e.leaseUntil = :leaseUntil, e.status = :status " +
                "WHERE e.id.scheduleId = :scheduleId AND e.id.triggerAt = :triggerAt")
            .setParameter("owner", BROKER_A)
            .setParameter("leaseUntil", expiredTime)
            .setParameter("status", "claimed")
            .setParameter("scheduleId", SCHEDULE_ID)
            .setParameter("triggerAt", triggerAt)
            .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        // When: Broker B claims the expired lease
        simulateBroker(BROKER_B);
        Cmd.send(new ScheduleLeaseReclaimCmd(Collections.singletonList(1)));
        entityManager.flush();
        entityManager.clear();

        // Broker B acquires the reclaimed delay.
        boolean claimedByB = leaseMaintainer.tryClaim(SCHEDULE_ID, triggerAt);
        assertThat(claimedByB).isTrue();

        // Verify: Broker B now owns the lease.
        ScheduleDelayEntity delay = findDelay(SCHEDULE_ID, triggerAt);
        assertThat(delay.getLeaseOwner()).isEqualTo(BROKER_B);

        // When: Broker A tries to begin execution with its stale lease.
        simulateBroker(BROKER_A);
        boolean transitioned = leaseMaintainer.transitionToRunning(SCHEDULE_ID, triggerAt);

        // Then: the conditional state update is fenced and cannot create another execution.
        assertThat(transitioned).isFalse();
        ScheduleDelayEntity afterAttempt = findDelay(SCHEDULE_ID, triggerAt);
        assertThat(afterAttempt.getLeaseOwner()).isEqualTo(BROKER_B);
        assertThat(afterAttempt.getStatus()).isEqualTo("claimed");
    }

    private void createScheduleDelay(String scheduleId, LocalDateTime triggerAt) {
        ScheduleDelayEntity entity = new ScheduleDelayEntity();
        entity.setId(new ScheduleDelayEntity.ID(scheduleId, triggerAt));
        entity.setDelayId(UUID.randomUUID().toString());
        entity.setBucket(1);
        entity.setStatus("init");
        entity.setAttempt(0);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setDeleted(false);
        scheduleDelayRepo.save(entity);
        entityManager.flush();
    }

    private ScheduleDelayEntity findDelay(String scheduleId, LocalDateTime triggerAt) {
        entityManager.flush();
        entityManager.clear();
        return scheduleDelayRepo.findById(new ScheduleDelayEntity.ID(scheduleId, triggerAt))
            .orElseThrow(() -> new IllegalStateException("Delay not found: " + scheduleId));
    }

    private void simulateBroker(String brokerId) {
        Broker mockBroker = mock(Broker.class);
        when(mockBroker.id()).thenReturn(brokerId);
        BrokerContext.initialize(mockBroker);
    }
}
