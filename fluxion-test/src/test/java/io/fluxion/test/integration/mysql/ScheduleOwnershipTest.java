/*
 * Copyright 2025-2030 Fluxion Team (https://github.com/Fluxion-io).
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
import io.fluxion.server.core.schedule.cmd.ScheduleTriggerCmd;
import io.fluxion.server.core.schedule.service.ScheduleCommandService;
import io.fluxion.server.infrastructure.dao.entity.BucketEntity;
import io.fluxion.server.infrastructure.dao.entity.ScheduleEntity;
import io.fluxion.server.infrastructure.dao.entity.TriggerEntity;
import io.fluxion.server.infrastructure.dao.entity.VersionEntity;
import io.fluxion.server.infrastructure.dao.repository.BucketEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.ScheduleEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.TriggerEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.VersionEntityRepo;
import io.fluxion.server.infrastructure.dao.tx.TransactionService;
import io.fluxion.server.infrastructure.schedule.task.DelayedTask;
import io.fluxion.server.infrastructure.schedule.scheduler.DelayedTaskScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Schedule 处理所有权（见 CONTEXT.md「Schedule 处理所有权」）：
 * 触发处理在自身事务内锁 Bucket 行校验当前 Broker 为 Owner、再锁 Schedule 行读最新配置与游标；
 * 非 Owner 直接退出不推进游标；Bucket 转移后旧 Owner 不得继续推进；
 * 命令只带 scheduleId，处理始终使用库内最新状态，不存在陈旧快照参与计算。
 */
@SpringBootTest(classes = MySqlTestApplication.class)
@ActiveProfiles("test-mysql")
@DisplayName("Schedule ownership & handover (H2 row-level)")
class ScheduleOwnershipTest extends AbstractMySqlIntegrationTest {

    private static final long INTERVAL_S = 60L;

    @Autowired
    private ScheduleCommandService scheduleCommandService;

    @Autowired
    private ScheduleEntityRepo scheduleEntityRepo;

    @Autowired
    private TriggerEntityRepo triggerEntityRepo;

    @Autowired
    private VersionEntityRepo versionEntityRepo;

    @Autowired
    private BucketEntityRepo bucketEntityRepo;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private EntityManager entityManager;

    private final List<String> seededScheduleIds = new ArrayList<>();
    private final List<String> seededBrokerIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        BrokerContext.initialize(null);
        if (!seededScheduleIds.isEmpty()) {
            transactionService.transactional(() -> {
                entityManager.createQuery("delete from ExecutionEntity e where e.triggerId in :ids")
                    .setParameter("ids", new ArrayList<>(seededScheduleIds)).executeUpdate();
                entityManager.createQuery("delete from ScheduleEntity s where s.scheduleId in :ids")
                    .setParameter("ids", new ArrayList<>(seededScheduleIds)).executeUpdate();
                entityManager.createQuery("delete from VersionEntity v where v.id.refId in :ids")
                    .setParameter("ids", new ArrayList<>(seededScheduleIds)).executeUpdate();
                entityManager.createQuery("delete from TriggerEntity t where t.triggerId in :ids")
                    .setParameter("ids", new ArrayList<>(seededScheduleIds)).executeUpdate();
            });
        }
        if (!seededBrokerIds.isEmpty()) {
            transactionService.transactional(() ->
                entityManager.createQuery("delete from BucketEntity b where b.brokerId in :brokers")
                    .setParameter("brokers", new ArrayList<>(seededBrokerIds))
                    .executeUpdate());
        }
        seededScheduleIds.clear();
        seededBrokerIds.clear();
    }

    @Test
    @DisplayName("O1: 非 Bucket Owner：静默退出，不推进游标、不建 Execution")
    void nonOwnerSilentlySkipsWithoutCursorAdvance() {
        String scheduleId = "trigger-" + UUID.randomUUID();
        Instant anchor = seedSchedule(scheduleId, INTERVAL_S, Instant.now().minusSeconds(2));
        // bucket 归属 owner，当前上下文却持有 intruder
        String ownerId = "broker-owner-" + UUID.randomUUID();
        ownBucket(ownerId, scheduleId);
        contextAs("broker-intruder-" + UUID.randomUUID());

        scheduleCommandService.handle(new ScheduleTriggerCmd(scheduleId));

        ScheduleEntity after = scheduleEntityRepo.findById(scheduleId).orElseThrow();
        assertThat(after.getLastTriggerAt()).isEqualTo(anchor.minusSeconds(INTERVAL_S));
        assertThat(after.getNextTriggerAt()).isEqualTo(anchor);
        assertThat(countExecutions(scheduleId)).isZero();

        // Owner 获得上下文后正常推进
        contextAs(ownerId);
        scheduleCommandService.handle(new ScheduleTriggerCmd(scheduleId));

        ScheduleEntity progressed = scheduleEntityRepo.findById(scheduleId).orElseThrow();
        assertThat(progressed.getLastTriggerAt()).isAfter(anchor);
        assertThat(progressed.getNextTriggerAt()).isAfter(Instant.now().plusSeconds(300));
        assertThat(countExecutions(scheduleId)).isGreaterThan(0);
    }

    @Test
    @DisplayName("O2: Bucket 转移后旧 Owner 不得推进，新 Owner 也不重复推进既有窗口")
    void handoverRefusesOldOwnerAndKeepsNewOwnerStable() {
        String scheduleId = "trigger-" + UUID.randomUUID();
        Instant anchor = seedSchedule(scheduleId, INTERVAL_S, Instant.now().minusSeconds(2));
        String oldOwner = "broker-old-" + UUID.randomUUID();
        ownBucket(oldOwner, scheduleId);
        contextAs(oldOwner);
        scheduleCommandService.handle(new ScheduleTriggerCmd(scheduleId));

        ScheduleEntity afterOld = scheduleEntityRepo.findById(scheduleId).orElseThrow();
        int executionsAfterOld = countExecutions(scheduleId);
        assertThat(afterOld.getLastTriggerAt()).isAfter(anchor);

        // Bucket 转移给新 Owner（重平衡语义：直接改写归属）
        String newOwner = "broker-new-" + UUID.randomUUID();
        ownBucket(newOwner, scheduleId);

        // 旧 Owner 再次触发：静默退出，游标与实例数不变
        scheduleCommandService.handle(new ScheduleTriggerCmd(scheduleId));
        ScheduleEntity afterOldRetry = scheduleEntityRepo.findById(scheduleId).orElseThrow();
        assertThat(afterOldRetry.getLastTriggerAt()).isEqualTo(afterOld.getLastTriggerAt());
        assertThat(afterOldRetry.getNextTriggerAt()).isEqualTo(afterOld.getNextTriggerAt());
        assertThat(countExecutions(scheduleId)).isEqualTo(executionsAfterOld);

        // 新 Owner 接手：读 DB 状态——窗口已远置未来，无新触发点，也无重复创建
        contextAs(newOwner);
        scheduleCommandService.handle(new ScheduleTriggerCmd(scheduleId));
        ScheduleEntity afterNew = scheduleEntityRepo.findById(scheduleId).orElseThrow();
        assertThat(afterNew.getLastTriggerAt()).isEqualTo(afterOld.getLastTriggerAt());
        assertThat(countExecutions(scheduleId)).isEqualTo(executionsAfterOld);
    }

    @Test
    @DisplayName("O3: 处理基于库内最新配置推进：命令构造后的配置变更（60s→120s）按新栅格生效")
    void handlerAlwaysUsesLatestDbStateNotStaleSnapshot() {
        String scheduleId = "trigger-" + UUID.randomUUID();
        Instant anchor = seedSchedule(scheduleId, INTERVAL_S,
            Instant.now().minus(Duration.ofMinutes(65)).truncatedTo(ChronoUnit.SECONDS));
        // 命令构造之后、处理发生前，配置被改为 120s 间隔：处理必须使用 DB 最新状态
        transactionService.transactional(() -> {
            ScheduleEntity entity = scheduleEntityRepo.findById(scheduleId).orElseThrow();
            entity.setScheduleInterval(120L);
            entity.setNextTriggerAt(anchor);
            scheduleEntityRepo.saveAndFlush(entity);
        });
        String owner = "broker-o3-" + UUID.randomUUID();
        ownBucket(owner, scheduleId);
        contextAs(owner);

        scheduleCommandService.handle(new ScheduleTriggerCmd(scheduleId));

        ScheduleEntity after = scheduleEntityRepo.findById(scheduleId).orElseThrow();
        assertThat(countExecutions(scheduleId)).isGreaterThan(0);
        // 推进的游标必须落在以 anchor 为锚的 120s 栅格上（旧快照的 60s 栅格不满足）
        assertThat(Duration.between(anchor, after.getLastTriggerAt()).getSeconds() % 120).isZero();
    }

    // ==================== helpers ====================

    /** 与 BucketCommandService.BUCKET_SIZE=64 相同的派生规则（仅测试种子用） */
    private static int allotBucket(String resourceId) {
        return Math.abs(resourceId.hashCode()) % 64 + 1;
    }

    /** 以 brokerId 初始化 BrokerContext（时间轮只记录不执行） */
    private void contextAs(String brokerId) {
        DelayedTaskScheduler scheduler = mock(DelayedTaskScheduler.class);
        doAnswer(inv -> null).when(scheduler).schedule(any(DelayedTask.class));
        Broker broker = mock(Broker.class);
        when(broker.id()).thenReturn(brokerId);
        when(broker.delayedTaskScheduler()).thenReturn(scheduler);
        BrokerContext.initialize(broker);
        seededBrokerIds.add(brokerId);
    }

    /** 将 scheduleId 派生 bucket 的归属改写为 brokerId */
    private void ownBucket(String brokerId, String scheduleId) {
        Integer derived = allotBucket(scheduleId);
        BucketEntity bucket = bucketEntityRepo.findById(derived).orElse(null);
        if (bucket == null) {
            bucket = new BucketEntity();
            bucket.setBucket(derived);
        }
        bucket.setBrokerId(brokerId);
        bucketEntityRepo.saveAndFlush(bucket);
        seededBrokerIds.add(brokerId);
    }

    /** 60s FIXED_RATE 调度 + Trigger/Version 链；nextTriggerAt=锚点，lastTriggerAt=前一格 */
    private Instant seedSchedule(String scheduleId, long intervalSeconds, Instant nextTriggerAt) {
        seedTriggerChain(scheduleId);
        ScheduleEntity entity = new ScheduleEntity();
        entity.setScheduleId(scheduleId);
        entity.setBucket(allotBucket(scheduleId));
        entity.setScheduleType("fixed_rate");
        entity.setStartTime(nextTriggerAt.minus(Duration.ofHours(2)));
        entity.setEndTime(nextTriggerAt.plus(Duration.ofDays(365)));
        entity.setScheduleDelay(0L);
        entity.setScheduleInterval(intervalSeconds);
        entity.setScheduleCron("");
        entity.setScheduleCronType("");
        entity.setTimeZone("UTC");
        entity.setMisfirePolicy("FIRE_RETRY");
        entity.setMaxFireAttempts(1);
        entity.setRecoveryEnabled(false);
        entity.setLastTriggerAt(nextTriggerAt.minusSeconds(intervalSeconds));
        entity.setNextTriggerAt(nextTriggerAt);
        entity.setEnabled(true);
        entity.setDeleted(false);
        scheduleEntityRepo.saveAndFlush(entity);
        seededScheduleIds.add(scheduleId);
        return nextTriggerAt;
    }

    private void seedTriggerChain(String scheduleId) {
        TriggerEntity trigger = new TriggerEntity();
        trigger.setTriggerId(scheduleId);
        trigger.setName("ownership-test");
        trigger.setPublishVersion("v1");
        trigger.setEnabled(true);
        trigger.setDeleted(false);
        triggerEntityRepo.saveAndFlush(trigger);

        VersionEntity version = new VersionEntity();
        version.setId(new VersionEntity.ID(scheduleId, "trigger", "v1"));
        version.setConfig("{\"type\":\"schedule\",\"executeConfig\":{"
            + "\"type\":\"executor\",\"executor\":{"
            + "\"type\":\"shell_script\",\"script\":\"echo ownership\"}}}");
        version.setDeleted(false);
        versionEntityRepo.saveAndFlush(version);
    }

    private int countExecutions(String triggerId) {
        Long count = entityManager.createQuery(
                "select count(e) from ExecutionEntity e where e.triggerId = :triggerId", Long.class)
            .setParameter("triggerId", triggerId)
            .getSingleResult();
        return count == null ? 0 : count.intValue();
    }
}