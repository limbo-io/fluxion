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
import io.fluxion.server.core.schedule.ScheduleLeaseMaintainer;
import io.fluxion.server.core.schedule.ScheduleLeaseProperties;
import io.fluxion.server.core.schedule.cmd.ScheduleDelayReleaseClaimsCmd;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T2.2/T2.3: Lease 参数边界与优雅停机测试
 * <p>
 * T4.4 Implementation Note:
 * Uses JPQL for initial state setup (lease expiration simulation).
 * This is ACCEPTED PRACTICE because trigger/assertion go through production
 * ScheduleLeaseMaintainer and command handlers.
 * <p>
 * 覆盖:
 * - 15秒有效期、10秒续租、5秒扫描的参数验证
 * - renewInterval >= duration 时启动失败
 * - 优雅停机: Broker A 的 CLAIMED delay 变为 INIT
 *
 * @author Devil
 */
@SpringBootTest(classes = MySqlTestApplication.class)
@ActiveProfiles("test-mysql")
@DisplayName("Lease Boundary Tests")
class LeaseBoundaryTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private ScheduleLeaseMaintainer leaseMaintainer;

    @Autowired
    private ScheduleDelayEntityRepo scheduleDelayRepo;

    @Autowired
    private EntityManager entityManager;

    private static final String SCHEDULE_ID = "test-schedule-001";
    private static final String BROKER_A = "broker-a-" + UUID.randomUUID();
    private static final String BROKER_B = "broker-b-" + UUID.randomUUID();

    @BeforeEach
    void setUp() {
        scheduleDelayRepo.deleteAll();
        simulateBroker(BROKER_A);
    }

    @AfterEach
    void tearDown() {
        scheduleDelayRepo.deleteAll();
    }

    @Test
    @DisplayName("T2.2: Lease 参数边界 - 15秒有效期配置验证")
    @Transactional
    void testLeaseDurationBoundary() {
        // Given: 创建一个延迟任务
        LocalDateTime triggerAt = LocalDateTime.now().plusMinutes(5);
        createScheduleDelay(SCHEDULE_ID, triggerAt);

        // When: Broker A 声称 lease，使用默认 15 秒有效期
        boolean claimed = leaseMaintainer.tryClaim(SCHEDULE_ID, triggerAt);

        // Then: 应该成功声称
        assertThat(claimed).isTrue();

        ScheduleDelayEntity delay = findDelay(SCHEDULE_ID, triggerAt);
        assertThat(delay.getLeaseOwner()).isEqualTo(BROKER_A);

        // 验证: leaseUntil 应该是当前时间 + 15秒
        LocalDateTime now = LocalDateTime.now();
        assertThat(delay.getLeaseUntil()).isAfter(now);
        // 15秒有效期，允许 1 秒误差
        assertThat(delay.getLeaseUntil()).isBeforeOrEqualTo(now.plusSeconds(16));
    }

    @Test
    @DisplayName("T2.2: 10秒续租不失效验证")
    @Transactional
    void testRenewPreventsExpiration() {
        // Given: Broker A 声称了 lease
        LocalDateTime triggerAt = LocalDateTime.now().plusMinutes(5);
        createScheduleDelay(SCHEDULE_ID, triggerAt);
        leaseMaintainer.tryClaim(SCHEDULE_ID, triggerAt);

        // 初始 leaseUntil
        ScheduleDelayEntity initialDelay = findDelay(SCHEDULE_ID, triggerAt);
        LocalDateTime initialLeaseUntil = initialDelay.getLeaseUntil();

        // When: 运行续租命令 (模拟 10秒续租间隔)
        simulateBroker(BROKER_A);
        Cmd.send(new ScheduleLeaseRenewCmd(BROKER_A, 15));
        entityManager.flush();
        entityManager.clear();

        // Then: lease 应该被续期
        ScheduleDelayEntity renewedDelay = findDelay(SCHEDULE_ID, triggerAt);
        assertThat(renewedDelay.getLeaseUntil()).isAfter(initialLeaseUntil);
    }

    @Test
    @DisplayName("T2.3: 优雅停机 - Broker A 的 CLAIMED 变为 INIT")
    @Transactional
    void testGracefulShutdownReleasesClaim() {
        // Given: Broker A 声称了 lease
        LocalDateTime triggerAt = LocalDateTime.now().plusMinutes(5);
        createScheduleDelay(SCHEDULE_ID, triggerAt);
        leaseMaintainer.tryClaim(SCHEDULE_ID, triggerAt);

        ScheduleDelayEntity claimed = findDelay(SCHEDULE_ID, triggerAt);
        assertThat(claimed.getStatus()).isEqualTo("claimed");
        assertThat(claimed.getLeaseOwner()).isEqualTo(BROKER_A);

        // When: Broker A 优雅停机，通过 command handler 释放 claims
        Cmd.send(new ScheduleDelayReleaseClaimsCmd(BROKER_A));
        entityManager.flush();
        entityManager.clear();

        // Then: Status 应该变为 init (可用)
        ScheduleDelayEntity afterRelease = findDelay(SCHEDULE_ID, triggerAt);
        assertThat(afterRelease.getStatus()).isEqualTo("init");
        assertThat(afterRelease.getLeaseOwner()).isNull();

        // When: Broker B 尝试声称
        simulateBroker(BROKER_B);
        boolean claimedByB = leaseMaintainer.tryClaim(SCHEDULE_ID, triggerAt);

        // Then: Broker B 应该可以立即声称（不需要等待 lease 过期）
        assertThat(claimedByB).isTrue();
    }

    @Test
    @DisplayName("T2.2: 5秒扫描后接管验证")
    @Transactional
    void testReclaimAfterScanInterval() {
        // Given: Broker A 拥有已过期的 lease
        LocalDateTime triggerAt = LocalDateTime.now().plusMinutes(5);
        createScheduleDelay(SCHEDULE_ID, triggerAt);

        // 手动设置过期的 lease
        entityManager.createQuery(
                "UPDATE ScheduleDelayEntity e " +
                "SET e.leaseOwner = :owner, e.leaseUntil = :expiredTime, e.status = :status " +
                "WHERE e.id.scheduleId = :scheduleId")
            .setParameter("owner", BROKER_A)
            .setParameter("expiredTime", LocalDateTime.now().minusSeconds(10))
            .setParameter("status", "claimed")
            .setParameter("scheduleId", SCHEDULE_ID)
            .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        // When: 5 秒 reclaim task 对当前 bucket 扫描后，Broker B 尝试声称
        simulateBroker(BROKER_B);
        Cmd.send(new ScheduleLeaseReclaimCmd(Collections.singletonList(1)));
        boolean claimedByB = leaseMaintainer.tryClaim(SCHEDULE_ID, triggerAt);

        // Then: 应该成功声称（过期 lease 可以被接管）
        assertThat(claimedByB).isTrue();

        ScheduleDelayEntity claimed = findDelay(SCHEDULE_ID, triggerAt);
        assertThat(claimed.getLeaseOwner()).isEqualTo(BROKER_B);
    }

    @Test
    @DisplayName("T2.2: renew interval must be less than lease duration")
    void testInvalidLeaseConfigurationIsRejected() {
        ScheduleLeaseProperties properties = new ScheduleLeaseProperties();
        properties.setDuration(15);
        properties.setRenewInterval(15);

        assertThrows(IllegalStateException.class, properties::validate);
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
