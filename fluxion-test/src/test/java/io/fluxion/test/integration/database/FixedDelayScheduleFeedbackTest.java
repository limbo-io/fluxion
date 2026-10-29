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

package io.fluxion.test.integration.database;

import io.fluxion.server.core.broker.Broker;
import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.execution.cmd.ExecutionSuccessCmd;
import io.fluxion.server.core.execution.cmd.ExecutionsLoadCmd;
import io.fluxion.server.core.execution.service.ExecutionCommandService;
import io.fluxion.server.core.execution.service.ExecutionScheduleCommandService;
import io.fluxion.server.core.schedule.cmd.ScheduleEnableCmd;
import io.fluxion.server.core.schedule.service.ScheduleCommandService;
import io.fluxion.server.infrastructure.dao.entity.BucketEntity;
import io.fluxion.server.infrastructure.dao.entity.ExecutionEntity;
import io.fluxion.server.infrastructure.dao.entity.ScheduleEntity;
import io.fluxion.server.infrastructure.dao.entity.TriggerEntity;
import io.fluxion.server.infrastructure.dao.entity.VersionEntity;
import io.fluxion.server.infrastructure.dao.repository.BucketEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.ExecutionEntityRepo;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 调度反馈回归：FIXED_DELAY 依赖 Execution 终态反馈推进（见 CONTEXT.md「调度反馈」）。
 * <p>
 * 覆盖历史上会卡死/崩溃的四条链：
 * <ul>
 *   <li>首次成功反馈推算出下一个触发点并创建 PENDING 实例（曾因反馈用旧值计算而 NPE 回滚）；</li>
 *   <li>在途窗口（nextTriggerAt=null）内重新启用不再 NPE；</li>
 *   <li>调度已删除时终态迁移不被反馈链 NPE 回滚；</li>
 *   <li>misfire SKIP 终态同样反馈，调度不会永久停摆。</li>
 * </ul>
 */
@SpringBootTest(classes = MySqlTestApplication.class)
@ActiveProfiles("test-mysql")
@DisplayName("FIXED_DELAY schedule feedback progression (H2 row-level)")
class FixedDelayScheduleFeedbackTest extends AbstractMySqlIntegrationTest {

    private static final Duration INTERVAL = Duration.ofSeconds(300);

    @Autowired
    private ExecutionScheduleCommandService executionScheduleCommandService;

    @Autowired
    private ExecutionCommandService executionCommandService;

    @Autowired
    private ScheduleCommandService scheduleCommandService;

    @Autowired
    private ExecutionEntityRepo executionEntityRepo;

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

    /** 本类 seed 的 triggerId / brokerId：测试后清除，保持共享测试库零足迹 */
    private final List<String> seededTriggerIds = new ArrayList<>();
    private final List<String> seededBrokerIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        BrokerContext.initialize(null);
        // 共享 H2 库：清除本类提交过的 Execution 行与 bucket 归属，
        // 避免污染其它类的全库计数断言（ObservationOverview）与 bucket 种子假设（MultiBroker）
        if (seededTriggerIds.isEmpty() && seededBrokerIds.isEmpty()) {
            return;
        }
        transactionService.transactional(() -> {
            if (!seededTriggerIds.isEmpty()) {
                entityManager.createQuery("delete from ExecutionEntity e where e.triggerId in :ids")
                    .setParameter("ids", new ArrayList<>(seededTriggerIds)).executeUpdate();
            }
            if (!seededBrokerIds.isEmpty()) {
                entityManager.createQuery("delete from BucketEntity b where b.brokerId in :brokers")
                    .setParameter("brokers", new ArrayList<>(seededBrokerIds)).executeUpdate();
            }
        });
        seededTriggerIds.clear();
        seededBrokerIds.clear();
    }

    @Test
    @DisplayName("F1: 首次成功反馈以反馈时刻推算下一个触发点并创建 PENDING 实例")
    void firstFeedbackProgressesSchedule() {
        String triggerId = "trigger-" + UUID.randomUUID();
        seedTriggerChain(triggerId);
        seedFixDelaySchedule(triggerId, false, true);
        Instant firedAt = Instant.now().minus(Duration.ofSeconds(10));
        ExecutionEntity running = seedExecution(triggerId, "running", firedAt, 1);

        // 触发处理需在锁内校验 Bucket 所有权：反馈链推进必须由归属 Broker 完成
        recordingBrokerContext("broker-" + UUID.randomUUID(), triggerId);

        Instant before = Instant.now();
        Boolean ok = executionCommandService.handle(
            ExecutionSuccessCmd.builder().executionId(running.getExecutionId()).endAt(Instant.now()).build());

        assertThat(ok).isTrue();
        ExecutionEntity finished = executionEntityRepo.findById(running.getExecutionId()).orElseThrow();
        assertThat(finished.getStatus()).isEqualTo("succeed");

        ScheduleEntity after = scheduleEntityRepo.findById(triggerId).orElseThrow();
        // 反馈落库（修复前：整个事务因后续链路 NPE 回滚，反馈丢失）
        assertThat(after.getLastFeedbackAt()).isBetween(before, Instant.now());
        // 下一个触发点 = 反馈时刻 + 间隔
        assertThat(after.getLastTriggerAt()).isBetween(
            after.getLastFeedbackAt().plus(INTERVAL).minusSeconds(1),
            after.getLastFeedbackAt().plus(INTERVAL).plusSeconds(5));
        // 创建了唯一下一个 PENDING 实例
        ExecutionEntity next = executionEntityRepo
            .findByTriggerIdAndTriggerAt(triggerId, after.getLastTriggerAt());
        assertThat(next).isNotNull();
        assertThat(next.getStatus()).isEqualTo("pending");
        // FIXED_DELAY 触发后 nextTriggerAt 再次置空，等待新实例反馈
        assertThat(after.getNextTriggerAt()).isNull();
    }

    @Test
    @DisplayName("F2: 在途窗口（nextTriggerAt=null）内重新启用不抛 NPE 且保持启用")
    void enableDuringInflightWindowDoesNotThrow() {
        String triggerId = "trigger-" + UUID.randomUUID();
        // 初始为禁用；修复前：启用事务会因 NPE 回滚而永久卡在禁用
        seedFixDelaySchedule(triggerId, false, false);

        scheduleCommandService.handle(new ScheduleEnableCmd(triggerId));

        ScheduleEntity after = scheduleEntityRepo.findById(triggerId).orElseThrow();
        assertThat(after.isEnabled()).isTrue();
        // 空 nextTriggerAt 由反馈链路继续推进，启用本身不触发生成
        assertThat(after.getNextTriggerAt()).isNull();
    }

    @Test
    @DisplayName("F3: 调度已删除：misfire 终态正常落库，不被反馈链 NPE 回滚")
    void misfireSkipOnDeletedScheduleStillPersists() {
        String triggerId = "trigger-" + UUID.randomUUID();
        // 调度已删除；保留一个过期的 PENDING 实例
        seedFixDelaySchedule(triggerId, true, true);
        ExecutionEntity misfired = seedExecution(triggerId, "pending",
            Instant.now().minus(Duration.ofSeconds(30)), allotBucket(triggerId));

        recordingBrokerContext("broker-" + UUID.randomUUID(), triggerId);
        executionScheduleCommandService.handle(new ExecutionsLoadCmd());

        ExecutionEntity after = executionEntityRepo.findById(misfired.getExecutionId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo("skipped");
    }

    @Test
    @DisplayName("F4: misfire SKIP 终态同样反馈：调度推进并创建下一个 PENDING 实例")
    void misfireSkipStillProgressesSchedule() {
        String triggerId = "trigger-" + UUID.randomUUID();
        seedTriggerChain(triggerId);
        seedFixDelaySchedule(triggerId, false, true);
        ExecutionEntity misfired = seedExecution(triggerId, "pending",
            Instant.now().minus(Duration.ofSeconds(30)), allotBucket(triggerId));

        Instant before = Instant.now();
        recordingBrokerContext("broker-" + UUID.randomUUID(), triggerId);
        executionScheduleCommandService.handle(new ExecutionsLoadCmd());

        // 到期未领实例按策略置为 SKIPPED
        ExecutionEntity skipped = executionEntityRepo.findById(misfired.getExecutionId()).orElseThrow();
        assertThat(skipped.getStatus()).isEqualTo("skipped");

        ScheduleEntity after = scheduleEntityRepo.findById(triggerId).orElseThrow();
        assertThat(after.getLastFeedbackAt()).isBetween(before, Instant.now());
        assertThat(after.getLastTriggerAt()).isNotNull();
        // 修复前：SKIPPED 不反馈，nextTriggerAt 永久为 null，调度永久停摆
        ExecutionEntity next = executionEntityRepo
            .findByTriggerIdAndTriggerAt(triggerId, after.getLastTriggerAt());
        assertThat(next).isNotNull();
        assertThat(next.getStatus()).isEqualTo("pending");
    }

    // ==================== helpers ====================

    /**
     * FIXED_DELAY 调度：lastTriggerAt 已有值、nextTriggerAt=null、lastFeedbackAt=null（）
     * 即「在途等待反馈」状态，与真实触发路径 handle(ScheduleTriggerCmd) 落库后的状态一致。
     * deleted 参数控制是否已删除，enabled 参数控制是否已启用。
     */
    private void seedFixDelaySchedule(String triggerId, boolean deleted, boolean enabled) {
        Instant now = Instant.now();
        ScheduleEntity entity = new ScheduleEntity();
        entity.setScheduleId(triggerId);
        entity.setBucket(allotBucket(triggerId));
        entity.setScheduleType("fixed_delay");
        entity.setStartTime(now.minus(Duration.ofHours(1)));
        entity.setEndTime(now.plus(java.time.Duration.ofDays(365)));
        entity.setScheduleDelay(0L);
        entity.setScheduleInterval(INTERVAL.getSeconds());
        entity.setScheduleCron("");
        entity.setScheduleCronType("");
        // 基线 ScheduleOption 要求必填 IANA 时区：日历语义解译依赖。
        entity.setTimeZone("UTC");
        entity.setMisfirePolicy("SKIP");
        entity.setMaxFireAttempts(1);
        entity.setLastTriggerAt(now.minus(Duration.ofSeconds(20)));
        entity.setLastFeedbackAt(null);
        entity.setNextTriggerAt(null);
        entity.setEnabled(enabled);
        entity.setDeleted(deleted);
        scheduleEntityRepo.saveAndFlush(entity);
        seededTriggerIds.add(triggerId);
    }

    /** Trigger + Version(config) 链：反馈推进后的 ScheduleTriggerCmd 会在同事务内解析 Trigger/Executable 并创建实例 */
    private void seedTriggerChain(String triggerId) {
        TriggerEntity trigger = new TriggerEntity();
        trigger.setTriggerId(triggerId);
        trigger.setName("feedback-test");
        trigger.setPublishVersion("v1");
        trigger.setEnabled(true);
        trigger.setDeleted(false);
        triggerEntityRepo.saveAndFlush(trigger);

        VersionEntity version = new VersionEntity();
        version.setId(new VersionEntity.ID(triggerId, "trigger", "v1"));
        version.setConfig("{\"type\":\"schedule\",\"executeConfig\":{"
            + "\"type\":\"executor\",\"executor\":{"
            + "\"type\":\"shell_script\",\"script\":\"echo feedback\"}}}");
        version.setDeleted(false);
        versionEntityRepo.saveAndFlush(version);
    }

    private ExecutionEntity seedExecution(String triggerId, String status, Instant triggerAt, int bucket) {
        ExecutionEntity entity = new ExecutionEntity();
        entity.setExecutionId("execution-" + UUID.randomUUID());
        entity.setTriggerId(triggerId);
        entity.setTriggerType("schedule");
        entity.setExecutableId(triggerId);
        entity.setExecutableType("executor");
        entity.setExecutableVersion("v1");
        entity.setStatus(status);
        entity.setTriggerAt(triggerAt);
        entity.setBucket(bucket);
        entity.setFireAttempt(0);
        entity.setDeleted(false);
        return executionEntityRepo.saveAndFlush(entity);
    }
    /** mock BrokerContext + 只记录不执行的时间轮；scheduleId 反查出的 bucket 归属该 broker */
    private void recordingBrokerContext(String brokerId, String triggerId) {
        DelayedTaskScheduler scheduler = mock(DelayedTaskScheduler.class);
        doAnswer(inv -> null).when(scheduler).schedule(any(DelayedTask.class));
        Broker broker = mock(Broker.class);
        when(broker.id()).thenReturn(brokerId);
        when(broker.delayedTaskScheduler()).thenReturn(scheduler);
        BrokerContext.initialize(broker);
        // 触发处理以 BucketAllotCmd(scheduleId) 反查 bucket 并校验归属：
        // 种子必须与该派生 bucket 对齐，bucket=1 的旧约定不再成立
        Integer derived = allotBucket(triggerId);
        BucketEntity bucket = bucketEntityRepo.findById(derived).orElse(null);
        if (bucket == null) {
            bucket = new BucketEntity();
            bucket.setBucket(derived);
        }
        bucket.setBrokerId(brokerId);
        bucketEntityRepo.saveAndFlush(bucket);
        seededBrokerIds.add(brokerId);
    }

    /** 与 BucketCommandService.BUCKET_SIZE=64 相同的派生规则（仅测试种子用） */
    private static int allotBucket(String resourceId) {
        return Math.abs(resourceId.hashCode()) % 64 + 1;
    }

}
