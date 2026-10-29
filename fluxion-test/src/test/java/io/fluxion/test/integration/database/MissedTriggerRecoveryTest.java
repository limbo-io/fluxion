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
import io.fluxion.server.core.schedule.ScheduleRecoveryMetrics;
import io.fluxion.server.core.schedule.cmd.ScheduleTriggerCmd;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 遗漏触发恢复语义（见 CONTEXT.md「Execution 预生成窗口」「遗漏触发恢复」）：
 * 未决区间内晚于 now-5s 的点按 10 分钟窗口正常预生成；更早点为遗漏候选——
 * 默认丢弃全部；开启后仅检查最接近 now 的 10 个原始点；已存在同点 Execution
 * 占位不扩张；SKIP、首批 Deadline 已过的点明确丢弃且不物化 SKIPPED/EXPIRED 实例。
 * <p>
 * 断言策略：锚点在种子时刻按秒截断，且 seed 与 handle 间的毫秒级差距远小于
 * 60s 间隔，全部计数/栅格点均可精确推导；跨指标断言只取本测试前后的 delta。
 */
@SpringBootTest(classes = MySqlTestApplication.class)
@ActiveProfiles("test-mysql")
@DisplayName("Missed-trigger recovery (H2 row-level)")
class MissedTriggerRecoveryTest extends AbstractMySqlIntegrationTest {

    /** FIXED_RATE 栅格间隔：60s */
    private static final long INTERVAL_S = 60L;
    /** 种子停机深度：65min（≫ 恢复上限 10 点，使计数断言与毫秒边界无关） */
    private static final long STALE_MIN = 65;

    @Autowired
    private ScheduleCommandService scheduleCommandService;

    @Autowired
    private ScheduleRecoveryMetrics recoveryMetrics;

    @Autowired
    private ScheduleEntityRepo scheduleEntityRepo;

    @Autowired
    private ExecutionEntityRepo executionEntityRepo;

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

    private final List<String> seededTriggerIds = new ArrayList<>();
    private final List<String> seededBrokerIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        BrokerContext.initialize(null);
        if (!seededTriggerIds.isEmpty()) {
            transactionService.transactional(() -> {
                entityManager.createQuery("delete from ExecutionEntity e where e.triggerId in :ids")
                    .setParameter("ids", new ArrayList<>(seededTriggerIds)).executeUpdate();
                entityManager.createQuery("delete from ScheduleEntity s where s.scheduleId in :ids")
                    .setParameter("ids", new ArrayList<>(seededTriggerIds)).executeUpdate();
                entityManager.createQuery("delete from VersionEntity v where v.id.refId in :ids")
                    .setParameter("ids", new ArrayList<>(seededTriggerIds)).executeUpdate();
                entityManager.createQuery("delete from TriggerEntity t where t.triggerId in :ids")
                    .setParameter("ids", new ArrayList<>(seededTriggerIds)).executeUpdate();
            });
        }
        if (!seededBrokerIds.isEmpty()) {
            transactionService.transactional(() -> entityManager.createQuery(
                    "delete from BucketEntity b where b.brokerId in :brokers")
                .setParameter("brokers", new ArrayList<>(seededBrokerIds))
                .executeUpdate());
        }
        seededTriggerIds.clear();
        seededBrokerIds.clear();
    }

    @Test
    @DisplayName("R1: 恢复关闭 + 停机 65min：全部遗漏点丢弃，无 Execution；游标推进到未来窗口")
    void recoveryDisabledDropsAllMissedPointsAndAdvancesCursor() {
        String triggerId = newTriggerId();
        long recoveredBefore = recoveryMetrics.getRecovered();
        Instant anchor = seedFixedRateSchedule(triggerId, "FIRE_RETRY", null, false);
        recordingBrokerContext("broker-r1-", triggerId);

        scheduleCommandService.handle(new ScheduleTriggerCmd(triggerId));

        // 65 分钟内的历史点一个都不建：全部执行点都在 now-5s 之后
        List<Instant> created = triggerAtsSorted(triggerId);
        assertThat(created).isNotEmpty();
        assertThat(created).allSatisfy(t -> assertThat(t).isAfter(Instant.now().minusSeconds(5)));

        ScheduleEntity after = scheduleEntityRepo.findById(triggerId).orElseThrow();
        // 游标已越过停机区间：lastTriggerAt 落在预生成窗口尾，nextTriggerAt 为其后继栅格点
        assertThat(after.getLastTriggerAt()).isEqualTo(anchor.plusSeconds(4500));
        assertThat(after.getNextTriggerAt()).isEqualTo(anchor.plusSeconds(4560));
        // 恢复关闭：不产生任何恢复决策
        assertThat(recoveryMetrics.getRecovered()).isEqualTo(recoveredBefore);

        // 幂等复跑：游标已在窗口外，无新增
        scheduleCommandService.handle(new ScheduleTriggerCmd(triggerId));
        assertThat(triggerAtsSorted(triggerId)).hasSize(created.size());
    }

    @Test
    @DisplayName("R2: 恢复开启 + 停机 65min：检查最新 10 个原始点并按时间正序创建")
    void recoveryEnabledRecoversLatestTenGridPoints() {
        String triggerId = newTriggerId();
        long recoveredBefore = recoveryMetrics.getRecovered();
        Instant anchor = seedFixedRateSchedule(triggerId, "FIRE_RETRY", null, true);
        recordingBrokerContext("broker-r2-", triggerId);

        scheduleCommandService.handle(new ScheduleTriggerCmd(triggerId));

        List<Instant> created = triggerAtsSorted(triggerId);
        // 10 个恢复点 + 11 个未来预生成点（[now-5s, now+600s] 内每 60s 一点）
        assertThat(created).hasSize(21);
        // 恢复点 = 最接近 now 的 10 个原始点，正序排列且都在栅格上
        List<Instant> expectedRecovered = new ArrayList<>();
        for (int k = 55; k <= 64; k++) {
            expectedRecovered.add(anchor.plusSeconds(k * INTERVAL_S));
        }
        assertThat(created.subList(0, 10)).containsExactlyElementsOf(expectedRecovered);
        assertThat(executionEntityRepo.findByTriggerIdAndTriggerAt(
            triggerId, anchor.plusSeconds(9 * INTERVAL_S))).isNull();
        assertThat(executionEntityRepo.findByTriggerIdAndTriggerAt(
            triggerId, anchor.plusSeconds(54 * INTERVAL_S))).isNull();
        // 未来窗口预生成：锚点后 65..75（含界）
        for (int k = 65; k <= 75; k++) {
            assertThat(created).contains(anchor.plusSeconds(k * INTERVAL_S));
        }
        // 游标推进：lastTriggerAt=75 号点，nextTriggerAt=76 号点；原始点此后不再回看
        ScheduleEntity after = scheduleEntityRepo.findById(triggerId).orElseThrow();
        assertThat(after.getLastTriggerAt()).isEqualTo(anchor.plusSeconds(75 * INTERVAL_S));
        assertThat(after.getNextTriggerAt()).isEqualTo(anchor.plusSeconds(76 * INTERVAL_S));

        assertThat(recoveryMetrics.getRecovered()).isEqualTo(recoveredBefore + 10);

        // 幂等复跑：不再回看，总数不变
        scheduleCommandService.handle(new ScheduleTriggerCmd(triggerId));
        assertThat(triggerAtsSorted(triggerId)).hasSize(21);
    }

    @Test
    @DisplayName("R3: 已存在的同点 Execution 占位：不重复建、不向更早历史扩张")
    void existingExecutionOccupiesRecoveryWindowWithoutExtending() {
        String triggerId = newTriggerId();
        long recoveredBefore = recoveryMetrics.getRecovered();
        Instant anchor = seedFixedRateSchedule(triggerId, "FIRE_RETRY", null, true);
        Instant occupiedPoint = anchor.plusSeconds(60 * INTERVAL_S);
        String occupiedExecutionId = seedExecution(triggerId, occupiedPoint);
        recordingBrokerContext("broker-r3-", triggerId);

        scheduleCommandService.handle(new ScheduleTriggerCmd(triggerId));

        // 占位点仍是原实例：未重复创建
        assertThat(executionEntityRepo.findByTriggerIdAndTriggerAt(triggerId, occupiedPoint)
            .getExecutionId()).isEqualTo(occupiedExecutionId);
        // 总行数：占位 1 + 新恢复 9 + 未来预生成 11 = 21（占位点本身计入候选名单）
        List<Instant> created = triggerAtsSorted(triggerId);
        assertThat(created).hasSize(21);
        // 范围仍是最新的 10 个原始点：十个候选点全部有行，且不向更早历史扩张
        for (int k = 55; k <= 64; k++) {
            assertThat(executionEntityRepo.findByTriggerIdAndTriggerAt(
                triggerId, anchor.plusSeconds(k * INTERVAL_S))).isNotNull();
        }
        assertThat(executionEntityRepo.findByTriggerIdAndTriggerAt(triggerId, anchor.plusSeconds(9 * INTERVAL_S))).isNull();
        assertThat(executionEntityRepo.findByTriggerIdAndTriggerAt(triggerId, anchor.plusSeconds(54 * INTERVAL_S))).isNull();
        // 已存在点与新建点都计入已恢复
        assertThat(recoveryMetrics.getRecovered()).isEqualTo(recoveredBefore + 10);
        // 游标正常推进
        ScheduleEntity after = scheduleEntityRepo.findById(triggerId).orElseThrow();
        assertThat(after.getLastTriggerAt()).isEqualTo(anchor.plusSeconds(75 * INTERVAL_S));
    }

    @Test
    @DisplayName("R4: 恢复点 Misfire=SKIP：明确丢弃，不物化 SKIPPED 实例，游标照常推进")
    void skipPolicyDropsRecoveredPointsWithoutSkippedExecutions() {
        String triggerId = newTriggerId();
        long recoveredBefore = recoveryMetrics.getRecovered();
        long droppedBefore = recoveryMetrics.getDropped();
        Instant anchor = seedFixedRateSchedule(triggerId, "SKIP", null, true);
        recordingBrokerContext("broker-r4-", triggerId);

        scheduleCommandService.handle(new ScheduleTriggerCmd(triggerId));

        // 遗漏区间内无任何 Execution：10 个候选点全部直接丢弃
        List<Instant> created = triggerAtsSorted(triggerId);
        for (int k = 55; k <= 64; k++) {
            assertThat(created).doesNotContain(anchor.plusSeconds(k * INTERVAL_S));
        }
        // 未来窗口照常预生成
        assertThat(created).hasSize(11);
        // 不创建 SKIPPED 实例
        assertThat(created).isNotEmpty();
        for (Instant t : created) {
            ExecutionEntity e = executionEntityRepo.findByTriggerIdAndTriggerAt(triggerId, t);
            assertThat(e.getStatus()).isEqualTo("pending");
        }
        // 游标照常推进越过全部遗漏点
        ScheduleEntity after = scheduleEntityRepo.findById(triggerId).orElseThrow();
        assertThat(after.getLastTriggerAt()).isEqualTo(anchor.plusSeconds(75 * INTERVAL_S));
        assertThat(after.getNextTriggerAt()).isEqualTo(anchor.plusSeconds(76 * INTERVAL_S));
        // 观测：10 个丢弃、0 个恢复
        assertThat(recoveryMetrics.getDropped()).isEqualTo(droppedBefore + 10);
        assertThat(recoveryMetrics.getRecovered()).isEqualTo(recoveredBefore);
    }

    @Test
    @DisplayName("R5: 首批 Deadline 已过的恢复点：明确丢弃，不物化必然 EXPIRED 的实例")
    void expiredDeadlineDropsRecoveredPoints() {
        String triggerId = newTriggerId();
        long droppedBefore = recoveryMetrics.getDropped();
        Instant anchor = seedFixedRateSchedule(triggerId, "FIRE_RETRY", 1000L, true);
        recordingBrokerContext("broker-r5-", triggerId);

        scheduleCommandService.handle(new ScheduleTriggerCmd(triggerId));

        // 遗漏点 triggerAt+1s 早都过期：全部丢弃，未来点照常建（未来 Deadline 未到）
        List<Instant> created = triggerAtsSorted(triggerId);
        for (int k = 55; k <= 64; k++) {
            assertThat(created).doesNotContain(anchor.plusSeconds(k * INTERVAL_S));
        }
        assertThat(created).hasSize(11);
        for (Instant t : created) {
            ExecutionEntity e = executionEntityRepo.findByTriggerIdAndTriggerAt(triggerId, t);
            assertThat(e.getDeadlineAt()).isEqualTo(t.plusMillis(1000L));
            assertThat(e.getStatus()).isEqualTo("pending");
        }
        // 游标照常推进
        ScheduleEntity after = scheduleEntityRepo.findById(triggerId).orElseThrow();
        assertThat(after.getLastTriggerAt()).isEqualTo(anchor.plusSeconds(75 * INTERVAL_S));
        assertThat(recoveryMetrics.getDropped()).isEqualTo(droppedBefore + 10);
    }

    @Test
    @DisplayName("R6: CRON 反向计算：最新 10 个原始点是真实日历触发点")
    void cronRecoveryUsesRealCalendarPoints() {
        String triggerId = newTriggerId();
        long recoveredBefore = recoveryMetrics.getRecovered();
        Instant anchor = seedCronEveryMinuteSchedule(triggerId, true);
        recordingBrokerContext("broker-r6-", triggerId);

        scheduleCommandService.handle(new ScheduleTriggerCmd(triggerId));

        List<Instant> created = triggerAtsSorted(triggerId);
        // 10 个恢复点：全部是整分钟日历点、正序、两两间隔 60s
        List<Instant> recoveredPoints = created.subList(0, 10);
        assertThat(recoveredPoints).allSatisfy(t -> {
            assertThat(t.truncatedTo(ChronoUnit.MINUTES)).isEqualTo(t);
        });
        for (int i = 1; i < recoveredPoints.size(); i++) {
            assertThat(Duration.between(recoveredPoints.get(i - 1), recoveredPoints.get(i)).getSeconds())
                .isEqualTo(60);
        }
        // 最新的原始点在 now-5s 之前、其后继已超出召回范围
        Instant newest = recoveredPoints.get(recoveredPoints.size() - 1);
        assertThat(newest).isBefore(Instant.now().minusSeconds(4));
        assertThat(newest.plusSeconds(60)).isAfter(Instant.now().minusSeconds(5));
        // 未来分钟点照常预生成
        assertThat(created.size()).isBetween(20, 22);
        assertThat(recoveryMetrics.getRecovered()).isEqualTo(recoveredBefore + 10);

        // 游标推进在真实日历点上
        ScheduleEntity after = scheduleEntityRepo.findById(triggerId).orElseThrow();
        assertThat(after.getLastTriggerAt()).isEqualTo(created.get(created.size() - 1));
        assertThat(after.getLastTriggerAt().truncatedTo(ChronoUnit.MINUTES)).isEqualTo(after.getLastTriggerAt());
    }

    @Test
    @DisplayName("R9: 落后不足 5 秒的点不算遗漏：正常预生成，不走恢复决策")
    void pointNewerThanThresholdIsPregeneratedNotRecovered() {
        String triggerId = newTriggerId();
        long recoveredBefore = recoveryMetrics.getRecovered();
        // 游标停在 2 秒前（不足 5s 阈值）
        Instant anchor = Instant.now().minusSeconds(2).truncatedTo(ChronoUnit.SECONDS);
        seedFixedRateSchedule(triggerId, "FIRE_RETRY", null, true, anchor);
        recordingBrokerContext("broker-r9-", triggerId);

        scheduleCommandService.handle(new ScheduleTriggerCmd(triggerId));

        // 该点正常建为 PENDING，且不产生恢复决策
        ExecutionEntity point = executionEntityRepo.findByTriggerIdAndTriggerAt(triggerId, anchor);
        assertThat(point).isNotNull();
        assertThat(point.getStatus()).isEqualTo("pending");
        assertThat(recoveryMetrics.getRecovered()).isEqualTo(recoveredBefore);
        // 锚点 + 60s..600s 共 10 个未来点
        assertThat(triggerAtsSorted(triggerId)).hasSize(11);
    }

    @Test
    @DisplayName("R10: 可执行体缺失：不推进游标不发布指标，等待修复后下一轮重试")
    void unresolvableCreationKeepsCursorUntouched() {
        String triggerId = newTriggerId();
        long recoveredBefore = recoveryMetrics.getRecovered();
        long droppedBefore = recoveryMetrics.getDropped();
        Instant anchor = seedFixedRateSchedule(triggerId, "FIRE_RETRY", null, true);
        // 删除触发链：模拟 Trigger 缺失/配置被清理，创建不可解析
        transactionService.transactional(() -> {
            entityManager.createQuery("delete from VersionEntity v where v.id.refId = :id")
                .setParameter("id", triggerId).executeUpdate();
            entityManager.createQuery("delete from TriggerEntity t where t.triggerId = :id")
                .setParameter("id", triggerId).executeUpdate();
        });
        recordingBrokerContext("broker-r10-", triggerId);

        scheduleCommandService.handle(new ScheduleTriggerCmd(triggerId));

        ScheduleEntity after = scheduleEntityRepo.findById(triggerId).orElseThrow();
        // 游标原样：lastTriggerAt/nextTriggerAt 都保持种子值；未创建任何 Execution
        assertThat(after.getLastTriggerAt()).isEqualTo(anchor.minusSeconds(INTERVAL_S));
        assertThat(after.getNextTriggerAt()).isEqualTo(anchor);
        assertThat(triggerAtsSorted(triggerId)).isEmpty();
        // 指标只反映已提交决策：失败后不发布任何恢复/丢弃计数
        assertThat(recoveryMetrics.getRecovered()).isEqualTo(recoveredBefore);
        assertThat(recoveryMetrics.getDropped()).isEqualTo(droppedBefore);
    }

    @Test
    @DisplayName("R11: 外层事务内竞争继续：同点幂等写入吸收冲突，后续点仍创建、游标提交、指标发布")
    void uniqueConflictContinuesRecoveryBatchInSameRun() throws Exception {
        String triggerId = newTriggerId();
        long recoveredBefore = recoveryMetrics.getRecovered();
        long droppedBefore = recoveryMetrics.getDropped();
        Instant anchor = seedFixedRateSchedule(triggerId, "FIRE_RETRY", null, true);
        Instant contestedPoint = anchor.plusSeconds(60 * INTERVAL_S);
        recordingBrokerContext("broker-r11-", triggerId);

        Racer racer = startRacerHoldingInsertAt(triggerId, contestedPoint);
        java.util.concurrent.atomic.AtomicReference<Throwable> handlerError =
            new java.util.concurrent.atomic.AtomicReference<>();
        try {
            assertThat(racer.inserted.await(10, TimeUnit.SECONDS)).isTrue();
            // 竞争点插入未提交：处理者候选预查不见它，会在同点幂等写入上阻塞在唯一索引等待
            Thread handler = new Thread(() -> {
                try {
                    scheduleCommandService.handle(new ScheduleTriggerCmd(triggerId));
                } catch (Throwable t) {
                    handlerError.set(t);
                }
            }, "r11-handler");
            handler.start();

            // 创建与游标同处外层事务：主线程看不到未提交进展，仅观测处理者进入锁等待
            awaitBlockedOrTimeout(handler);
            // 竞争者提交：处理者的同点幂等写入落为 no-op 幂等确认（EXISTING），不抛异常继续后续点
            racer.release();
            handler.join(30000);
            assertThat(handler.isAlive()).isFalse();
            assertThat(handlerError.get()).isNull();
        } finally {
            racer.release();
            racer.joinQuietly();
        }

        // 竞争点存在且仅有一行（竞争者那行）；批次内更早/更晚的恢复点与未来点全部创建
        ExecutionEntity contested = executionEntityRepo.findByTriggerIdAndTriggerAt(triggerId, contestedPoint);
        assertThat(contested).isNotNull();
        assertThat(contested.getExecutionId()).isEqualTo(racer.executionId);
        List<Instant> created = triggerAtsSorted(triggerId);
        assertThat(created).hasSize(21);
        assertThat(created).contains(contestedPoint);
        // 后续恢复点与未来预生成点未被竞争影响：61..64 + 65..75
        for (int k = 61; k <= 75; k++) {
            assertThat(created).contains(anchor.plusSeconds(k * INTERVAL_S));
        }
        // 游标提交：不再等待下一轮扫描
        ScheduleEntity after = scheduleEntityRepo.findById(triggerId).orElseThrow();
        assertThat(after.getLastTriggerAt()).isEqualTo(anchor.plusSeconds(75 * INTERVAL_S));
        assertThat(after.getNextTriggerAt()).isEqualTo(anchor.plusSeconds(76 * INTERVAL_S));
        // 已恢复计数含竞争点（9 新建 + 1 竞争确认）
        assertThat(recoveryMetrics.getRecovered()).isEqualTo(recoveredBefore + 10);
        assertThat(recoveryMetrics.getDropped()).isEqualTo(droppedBefore);
    }

    @Test
    @DisplayName("R12: 普通写入失败：批内 Execution 从未对外可见（含可领取状态）、游标不动、指标不增长")
    void ordinaryFailureKeepsBatchUnseenAndCursorIntact() throws Exception {
        String triggerId = newTriggerId();
        long recoveredBefore = recoveryMetrics.getRecovered();
        long droppedBefore = recoveryMetrics.getDropped();
        Instant anchor = seedFixedRateSchedule(triggerId, "FIRE_RETRY", null, true);
        Instant blockedPoint = anchor.plusSeconds(60 * INTERVAL_S);
        recordingBrokerContext("broker-r12-", triggerId);

        Racer racer = startRacerHoldingInsertAt(triggerId, blockedPoint);
        java.util.concurrent.atomic.AtomicReference<Throwable> handlerError =
            new java.util.concurrent.atomic.AtomicReference<>();
        try {
            assertThat(racer.inserted.await(10, TimeUnit.SECONDS)).isTrue();
            // 普通失败注入：竞争者不提交，处理者的同点幂等写入阻塞直至锁等待超时（HYT00）
            Thread handler = new Thread(() -> {
                try {
                    scheduleCommandService.handle(new ScheduleTriggerCmd(triggerId));
                } catch (Throwable t) {
                    handlerError.set(t);
                }
            }, "r12-handler");
            handler.start();
            // 处理者阻塞在 60 号点时，55..59 已在本事务写入但未提交：任何连接都不可见、不可领取
            awaitBlockedOrTimeout(handler);
            assertThat(triggerAtsSorted(triggerId)).isEmpty();
            // 锁等待超时后失败：外层事务回滚，本批 Execution 从未存在过
            handler.join(30_000);
            assertThat(handler.isAlive()).isFalse();
            assertThat(handlerError.get()).isNotNull();
        } finally {
            racer.rollbackOnRelease = true;
            racer.release();
            racer.joinQuietly();
        }

        // 整批真实回滚：无任何 Execution 遗留
        assertThat(triggerAtsSorted(triggerId)).isEmpty();
        // 游标原样不动
        ScheduleEntity after = scheduleEntityRepo.findById(triggerId).orElseThrow();
        assertThat(after.getLastTriggerAt()).isEqualTo(anchor.minusSeconds(INTERVAL_S));
        assertThat(after.getNextTriggerAt()).isEqualTo(anchor);
        // 失败不发布任何恢复/丢弃指标
        assertThat(recoveryMetrics.getRecovered()).isEqualTo(recoveredBefore);
        assertThat(recoveryMetrics.getDropped()).isEqualTo(droppedBefore);
    }

    @Test
    @DisplayName("R13: 普通失败整批回滚时，既有外部 PENDING 实例绝不被删除")
    void ordinaryFailureNeverDeletesExternalPendingExecution() throws Exception {
        String triggerId = newTriggerId();
        long recoveredBefore = recoveryMetrics.getRecovered();
        long droppedBefore = recoveryMetrics.getDropped();
        Instant anchor = seedFixedRateSchedule(triggerId, "FIRE_RETRY", null, true);
        // 外部已存在的 PENDING 实例：落在恢复槽内（k=57），会被本轮处理幂等确认跳过
        Instant externalPoint = anchor.plusSeconds(57 * INTERVAL_S);
        String externalId = seedExecution(triggerId, externalPoint);
        Instant blockedPoint = anchor.plusSeconds(60 * INTERVAL_S);
        recordingBrokerContext("broker-r13-", triggerId);

        Racer racer = startRacerHoldingInsertAt(triggerId, blockedPoint);
        java.util.concurrent.atomic.AtomicReference<Throwable> handlerError =
            new java.util.concurrent.atomic.AtomicReference<>();
        try {
            assertThat(racer.inserted.await(10, TimeUnit.SECONDS)).isTrue();
            // 处理者预查即见外部 57 号点：幂等确认跳过（计入本轮计数但随失败不发布）；
            // 55/56/58/59 未提交写入，60 号点阻塞直至锁等待超时→外层回滚
            Thread handler = new Thread(() -> {
                try {
                    scheduleCommandService.handle(new ScheduleTriggerCmd(triggerId));
                } catch (Throwable t) {
                    handlerError.set(t);
                }
            }, "r13-handler");
            handler.start();
            awaitBlockedOrTimeout(handler);
            handler.join(30_000);
            assertThat(handler.isAlive()).isFalse();
            assertThat(handlerError.get()).isNotNull();
        } finally {
            racer.rollbackOnRelease = true;
            racer.release();
            racer.joinQuietly();
        }

        // 外部行原样保留：executionId、状态均未变——任何路径都不得把它当成本批产物删除
        ExecutionEntity external = executionEntityRepo.findByTriggerIdAndTriggerAt(triggerId, externalPoint);
        assertThat(external).isNotNull();
        assertThat(external.getExecutionId()).isEqualTo(externalId);
        assertThat(external.getStatus()).isEqualTo("pending");
        // 该 trigger 下只剩外部那一行：本批全部随外层回滚未落库
        assertThat(triggerAtsSorted(triggerId)).containsExactly(externalPoint);
        // 游标原样不动
        ScheduleEntity after = scheduleEntityRepo.findById(triggerId).orElseThrow();
        assertThat(after.getLastTriggerAt()).isEqualTo(anchor.minusSeconds(INTERVAL_S));
        assertThat(after.getNextTriggerAt()).isEqualTo(anchor);
        // 失败不发布任何恢复/丢弃指标（外部点的幂等确认也不产生计数）
        assertThat(recoveryMetrics.getRecovered()).isEqualTo(recoveredBefore);
        assertThat(recoveryMetrics.getDropped()).isEqualTo(droppedBefore);
    }

    /**
     * 竞争者线程：在独立事务中插入 (triggerId, triggerAt) 同点 Execution 但不提交，
     * inserted 置位后长等 release；release 后按 rollbackOnRelease 决定提交或回滚。
     */
    private Racer startRacerHoldingInsertAt(String triggerId, Instant triggerAt) {
        Racer racer = new Racer();
        racer.executionId = "execution-racer-" + UUID.randomUUID();
        Thread thread = new Thread(() -> {
            try {
                transactionService.transactional(() -> {
                    ExecutionEntity entity = new ExecutionEntity();
                    entity.setExecutionId(racer.executionId);
                    entity.setTriggerId(triggerId);
                    entity.setTriggerType("schedule");
                    entity.setExecutableId(triggerId);
                    entity.setExecutableType("executor");
                    entity.setExecutableVersion("v1");
                    entity.setStatus("pending");
                    entity.setTriggerAt(triggerAt);
                    entity.setBucket(allotBucket(triggerId));
                    entity.setFireAttempt(0);
                    entity.setDeleted(false);
                    executionEntityRepo.saveAndFlush(entity);
                    racer.inserted.countDown();
                    try {
                        // 长等释放：期间事务保持未提交，同点插入在唯一索引上序列化
                        if (!racer.release.await(30, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("racer release timeout");
                        }
                        if (racer.rollbackOnRelease) {
                            // 仅用于注入锁竞争而不落库
                            throw new IllegalStateException("racer rollback by design");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("racer interrupted", e);
                    }
                    return null;
                });
            } catch (RuntimeException e) {
                // 释放失败/中断/设计内回滚：事务回滚收尾，不遗留锁
                racer.rolledBack = true;
            }
        }, "racer-hold-" + racer.executionId);
        racer.thread = thread;
        thread.start();
        return racer;
    }

    /** 等待处理者线程进入锁等待（WAITING/TIMED_WAITING 连续两次采样）；窗口内未见则超时后释放 */
    private void awaitBlockedOrTimeout(Thread handler) {
        long deadline = System.currentTimeMillis() + 8_000;
        int consecutive = 0;
        while (System.currentTimeMillis() < deadline) {
            Thread.State state = handler.getState();
            if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
                if (++consecutive >= 2) {
                    return;
                }
            } else {
                consecutive = 0;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        // 未见等待态：继续释放（两种路径的对外结果断言一致）
    }

    /** 竞争者句柄 */
    private static class Racer {
        volatile String executionId;
        volatile Thread thread;
        volatile boolean rolledBack;
        volatile boolean rollbackOnRelease;
        final CountDownLatch inserted = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        void release() {
            release.countDown();
        }

        void joinQuietly() {
            try {
                thread.join(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ==================== helpers ====================

    private String newTriggerId() {
        return "trigger-" + UUID.randomUUID();
    }

    /** 与 BucketCommandService.BUCKET_SIZE=64 相同的派生规则（仅测试种子用） */
    private static int allotBucket(String resourceId) {
        return Math.abs(resourceId.hashCode()) % 64 + 1;
    }

    private void recordingBrokerContext(String prefix, String scheduleId) {
        String brokerId = prefix + UUID.randomUUID();
        DelayedTaskScheduler scheduler = mock(DelayedTaskScheduler.class);
        doAnswer(inv -> null).when(scheduler).schedule(any(DelayedTask.class));
        Broker broker = mock(Broker.class);
        when(broker.id()).thenReturn(brokerId);
        when(broker.delayedTaskScheduler()).thenReturn(scheduler);
        BrokerContext.initialize(broker);
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

    /**
     * FIXED_RATE 60s 调度 + seedTriggerChain；nextTriggerAt 停在 {@code now-65min}
     * （未决区间起点/栅格锚点，秒截断），lastTriggerAt 锚点前一格。返回锚点。
     */
    private Instant seedFixedRateSchedule(String triggerId, String misfirePolicy,
                                          Long maxStartDelayMillis, boolean recoveryEnabled) {
        Instant anchor = Instant.now().minus(Duration.ofMinutes(STALE_MIN)).truncatedTo(ChronoUnit.SECONDS);
        return seedFixedRateSchedule(triggerId, misfirePolicy, maxStartDelayMillis, recoveryEnabled, anchor);
    }

    private Instant seedFixedRateSchedule(String triggerId, String misfirePolicy,
                                          Long maxStartDelayMillis, boolean recoveryEnabled, Instant anchor) {
        seedTriggerChain(triggerId);
        ScheduleEntity entity = new ScheduleEntity();
        entity.setScheduleId(triggerId);
        entity.setBucket(allotBucket(triggerId));
        entity.setScheduleType("fixed_rate");
        entity.setStartTime(anchor.minus(Duration.ofHours(2)));
        entity.setEndTime(anchor.plus(Duration.ofDays(365)));
        entity.setScheduleDelay(0L);
        entity.setScheduleInterval(INTERVAL_S);
        entity.setScheduleCron("");
        entity.setScheduleCronType("");
        entity.setTimeZone("UTC");
        entity.setMisfirePolicy(misfirePolicy);
        entity.setMaxFireAttempts(1);
        entity.setMaxStartDelayMillis(maxStartDelayMillis);
        entity.setRecoveryEnabled(recoveryEnabled);
        entity.setLastTriggerAt(anchor.minusSeconds(INTERVAL_S));
        entity.setLastFeedbackAt(null);
        entity.setNextTriggerAt(anchor);
        entity.setEnabled(true);
        entity.setDeleted(false);
        scheduleEntityRepo.saveAndFlush(entity);
        seededTriggerIds.add(triggerId);
        return anchor;
    }

    /** 每分钟整点的 QUARTZ CRON 调度（UTC）；nextTriggerAt 停在 65 分钟前的整分点 */
    private Instant seedCronEveryMinuteSchedule(String triggerId, boolean recoveryEnabled) {
        seedTriggerChain(triggerId);
        Instant anchor = Instant.now().minus(Duration.ofMinutes(STALE_MIN))
            .truncatedTo(ChronoUnit.MINUTES);
        ScheduleEntity entity = new ScheduleEntity();
        entity.setScheduleId(triggerId);
        entity.setBucket(allotBucket(triggerId));
        entity.setScheduleType("cron");
        entity.setStartTime(anchor.minus(Duration.ofHours(2)));
        entity.setEndTime(anchor.plus(Duration.ofDays(365)));
        entity.setScheduleDelay(0L);
        entity.setScheduleInterval(0L);
        entity.setScheduleCron("0 * * * * ?");
        entity.setScheduleCronType("QUARTZ");
        entity.setTimeZone("UTC");
        entity.setMisfirePolicy("FIRE_RETRY");
        entity.setMaxFireAttempts(1);
        entity.setRecoveryEnabled(recoveryEnabled);
        entity.setLastTriggerAt(anchor.minusSeconds(INTERVAL_S));
        entity.setNextTriggerAt(anchor);
        entity.setEnabled(true);
        entity.setDeleted(false);
        scheduleEntityRepo.saveAndFlush(entity);
        seededTriggerIds.add(triggerId);
        return anchor;
    }

    /** Trigger + Version(config) 链：触发处理创建 Execution 时会按 publishVersion 解析 */
    private void seedTriggerChain(String triggerId) {
        TriggerEntity trigger = new TriggerEntity();
        trigger.setTriggerId(triggerId);
        trigger.setName("recovery-test");
        trigger.setPublishVersion("v1");
        trigger.setEnabled(true);
        trigger.setDeleted(false);
        triggerEntityRepo.saveAndFlush(trigger);

        VersionEntity version = new VersionEntity();
        version.setId(new VersionEntity.ID(triggerId, "trigger", "v1"));
        version.setConfig("{\"type\":\"schedule\",\"executeConfig\":{"
            + "\"type\":\"executor\",\"executor\":{"
            + "\"type\":\"shell_script\",\"script\":\"echo recovery\"}}}");
        version.setDeleted(false);
        versionEntityRepo.saveAndFlush(version);
    }

    private String seedExecution(String triggerId, Instant triggerAt) {
        ExecutionEntity entity = new ExecutionEntity();
        entity.setExecutionId("execution-" + UUID.randomUUID());
        entity.setTriggerId(triggerId);
        entity.setTriggerType("schedule");
        entity.setExecutableId(triggerId);
        entity.setExecutableType("executor");
        entity.setExecutableVersion("v1");
        entity.setStatus("pending");
        entity.setTriggerAt(triggerAt);
        entity.setBucket(allotBucket(triggerId));
        entity.setFireAttempt(0);
        entity.setDeleted(false);
        executionEntityRepo.saveAndFlush(entity);
        return entity.getExecutionId();
    }

    /** 该 triggerId 下全部 Execution 的 triggerAt 升序集合 */
    private List<Instant> triggerAtsSorted(String triggerId) {
        List<Instant> result = new ArrayList<>();
        // 借用统计口径：全库扫描在测试规模下可接受
        entityManager.clear();
        List<ExecutionEntity> all = entityManager.createQuery(
                "select e from ExecutionEntity e where e.triggerId = :triggerId", ExecutionEntity.class)
            .setParameter("triggerId", triggerId)
            .getResultList();
        for (ExecutionEntity e : all) {
            result.add(e.getTriggerAt());
        }
        result.sort(Instant::compareTo);
        return result;
    }
}