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

package io.fluxion.server.core.schedule.service;

import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.broker.cmd.BucketAllotCmd;
import io.fluxion.server.core.execution.Executable;
import io.fluxion.server.core.execution.ExecutionStatus;
import io.fluxion.server.core.execution.cmd.ExecutionCreateCmd;
import io.fluxion.server.core.execution.query.ExecutableByIdQuery;
import io.fluxion.server.core.execution.service.ExecutionScheduleCommandService;
import io.fluxion.server.core.schedule.Schedule;
import io.fluxion.server.core.schedule.ScheduleConstants;
import io.fluxion.server.core.schedule.ScheduleRecoveryMetrics;
import io.fluxion.server.core.schedule.cmd.ScheduleDeleteCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleDisableCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleEnableCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleFeedbackCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleSaveCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleTriggerCmd;
import io.fluxion.server.core.schedule.converter.ScheduleEntityConverter;
import io.fluxion.server.core.trigger.Trigger;
import io.fluxion.server.core.trigger.TriggerType;
import io.fluxion.server.core.trigger.query.TriggerByIdQuery;
import io.fluxion.server.infrastructure.dao.entity.BucketEntity;
import io.fluxion.server.infrastructure.dao.entity.ScheduleEntity;
import io.fluxion.server.infrastructure.dao.repository.BucketEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.ExecutionEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.ScheduleEntityRepo;
import io.fluxion.server.infrastructure.schedule.BasicCalculation;
import io.fluxion.server.infrastructure.schedule.MisfirePolicy;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import io.fluxion.server.infrastructure.schedule.calculator.CronScheduleCalculator;
import io.limbo.utils.MD5Utils;
import io.limbo.utils.json.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import io.limbo.cqrs.spring.annotation.CommandHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import io.limbo.cqrs.core.commandhandling.Cmd;
import io.limbo.cqrs.core.queryhandling.Query;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Schedule 命令入口：配置持久化、启停、反馈与触发处理。
 * <p>
 * 触发处理（{@link #handle(ScheduleTriggerCmd)}）是推进 Schedule 触发游标的唯一路径：
 * 在同一事务内按固定顺序锁定 Bucket 行、校验当前 Broker 为 Bucket Owner、再锁定 Schedule 行，
 * 以库内最新配置与游标完成遗漏触发恢复与未来预生成（见 CONTEXT.md「Schedule 处理所有权」
 * 「遗漏触发恢复」）。游标仅在区间内每个点都已创建、按恢复规则明确丢弃或幂等确认后推进。
 * <p>
 * 整批原子边界：Execution 创建、游标更新与恢复指标注册都在同一外层事务——普通持久化
 * 异常时全部写入自然回滚，本批 Execution 对外从未可见（包括可领取状态），游标不推进，
 * afterCommit 指标回调也不发布。同一 (triggerId, triggerAt) 的身份竞争由 Execution 创建
 * 原语在数据库层幂等吸收：竞争不抛唯一键异常，该点视为已处理，本轮继续后续点。恢复指标
 * 仅在提交成功后发布。
 *
 * @author Devil
 */
@Slf4j
@Service
public class ScheduleCommandService {

    @Resource
    private ScheduleEntityRepo scheduleEntityRepo;

    @Resource
    private BucketEntityRepo bucketEntityRepo;

    @Resource
    private ExecutionEntityRepo executionEntityRepo;

    @Resource
    private EntityManager entityManager;

    @Resource
    private ScheduleRecoveryMetrics recoveryMetrics;

    @Transactional
    @CommandHandler
    public void handle(ScheduleSaveCmd cmd) {
        // 本命令会先写 Schedule 行、再嵌套触发（要 Bucket 行锁）；先锁 Bucket 保持与触发事务
        // 一致的 Bucket → Schedule 顺序，避免与其它处理者互锁
        lockBucketRow(cmd.getId());
        ScheduleEntity entity = scheduleEntityRepo.findByScheduleIdAndDeleted(cmd.getId(), false);
        if (entity == null) {
            // 新增
            entity = new ScheduleEntity();
            entity.setScheduleId(cmd.getId());
            BasicCalculation calculation = new BasicCalculation(
                    null, null, cmd.getOption()
            );
            entity.setNextTriggerAt(calculation.triggerAt());
            int bucket = Cmd.send(new BucketAllotCmd(entity.getScheduleId())).getBucket();
            entity.setBucket(bucket);
            // 配置信息
            ScheduleEntityConverter.assemble(entity, cmd.getOption());
            scheduleEntityRepo.saveAndFlush(entity);
            // 创建调度
            Cmd.send(new ScheduleTriggerCmd(entity.getScheduleId()));
        } else {
            String oldVersion = MD5Utils.md5(JacksonUtils.toJSONString(ScheduleEntityConverter.toOption(entity)));
            String newVersion = MD5Utils.md5(JacksonUtils.toJSONString(cmd.getOption()));
            ScheduleEntityConverter.assemble(entity, cmd.getOption());
            // 配置发布只失效未领取的未来实例；已领取或运行的实例保留版本快照。
            if (!oldVersion.equals(newVersion)) {
                entity.setLastTriggerAt(null);
                entity.setNextTriggerAt(new BasicCalculation(null, null, cmd.getOption()).triggerAt());
                scheduleEntityRepo.saveAndFlush(entity);
                invalidatePendingExecutions(cmd.getId());
                Cmd.send(new ScheduleTriggerCmd(cmd.getId()));
            } else {
                scheduleEntityRepo.saveAndFlush(entity);
            }
        }
    }

    @Transactional
    @CommandHandler
    public void handle(ScheduleDeleteCmd cmd) {
        scheduleEntityRepo.deleteById(cmd.getId()); // 软删除 交由 DataCleaner 删除
        invalidatePendingExecutions(cmd.getId());
    }

    @Transactional
    @CommandHandler
    public void handle(ScheduleEnableCmd cmd) {
        // 先锁 Bucket 再写 Schedule，锁顺序同上
        lockBucketRow(cmd.getId());
        scheduleEntityRepo.updateEnable(cmd.getId(), true);
        // 触发处理在自身事务内校验所有权并推进游标
        Cmd.send(new ScheduleTriggerCmd(cmd.getId()));
    }

    @Transactional
    @CommandHandler
    public void handle(ScheduleDisableCmd cmd) {
        scheduleEntityRepo.updateEnable(cmd.getId(), false);
        // 可以不删，不然开关会比较重
    }

    @Transactional
    @CommandHandler
    public void handle(ScheduleFeedbackCmd cmd) {
        Schedule schedule = cmd.getSchedule();
        Instant now = Instant.now();
        // 先锁 Bucket 再写反馈列，锁顺序同上
        lockBucketRow(schedule.getId());
        ScheduleEntity entity = scheduleEntityRepo.findByScheduleIdAndDeleted(schedule.getId(), false);
        if (entity == null) {
            return;
        }
        // 管理实体写入（非 JPQL 批量）：后续同事务内的触发处理读回时不会拿到旧的一级缓存实例
        entity.setLastFeedbackAt(now);
        // 用锁内读到的最新配置判断是否由反馈推进：不信任命令携带的快照——若调度已切换为
        // FIXED_DELAY，旧类型判断会漏推下一次触发使调度停摆（见 CONTEXT.md「调度反馈」）
        if (ScheduleType.FIXED_DELAY != ScheduleEntityConverter.toOption(entity).getType()) {
            return;
        }
        // FIXED_DELAY 由反馈推进：命令只带 scheduleId，处理事务在锁内重读最新反馈并计算下一触发点
        Cmd.send(new ScheduleTriggerCmd(schedule.getId()));
    }

    /**
     * Schedule 触发处理统一入口（见类注释）。非 Bucket Owner 或快照对应行已失效时直接退出，
     * 不推进游标。分支处理返回本轮恢复决策计数器，事务提交成功后才发布到观测指标。
     */
    @Transactional
    @CommandHandler
    public void handle(ScheduleTriggerCmd cmd) {
        if (BrokerContext.broker() == null) {
            // 无 Broker 运行上下文：无法完成所有权校验，交由 Broker 的 Loader 扫描处理
            return;
        }
        String scheduleId = cmd.getScheduleId();
        int bucket = Cmd.send(new BucketAllotCmd(scheduleId)).getBucket();
        BucketEntity bucketEntity = bucketEntityRepo.findWithLockingByBucket(bucket);
        if (bucketEntity == null || !Objects.equals(bucketEntity.getBrokerId(), BrokerContext.broker().id())) {
            // 非当前 Bucket Owner：不直接推进游标，等待 Owner Loader 扫描接管
            return;
        }
        ScheduleEntity entity = scheduleEntityRepo.findWithLockingByScheduleIdAndDeleted(scheduleId, false);
        if (entity == null || !entity.isEnabled()) {
            return;
        }
        ScheduleOption option = ScheduleEntityConverter.toOption(entity);
        Instant now = Instant.now();
        RecoveryCounters counters = ScheduleType.FIXED_DELAY == option.getType()
            ? handleFixedDelay(entity, option, now)
            : handleCalendarType(entity, option, now);
        publishAfterCommit(counters);
    }

    /**
     * FIXED_DELAY：完全由反馈推进的下一次触发（见 CONTEXT.md「调度反馈」）。触发处理只决定
     * 一个未来单点；在途实例未反馈时游标保持 nextTriggerAt=null，等待反馈推算。
     * FIXED_DELAY 不参与遗漏恢复，恒返回空计数器。
     */
    private RecoveryCounters handleFixedDelay(ScheduleEntity entity, ScheduleOption option, Instant now) {
        Instant nextTriggerAt = entity.getNextTriggerAt();
        if (nextTriggerAt == null || nextTriggerAt.isBefore(now)) {
            // 反馈产物或陈旧游标：按库内最新 lastTriggerAt/lastFeedbackAt 重新计算单点
            nextTriggerAt = new BasicCalculation(
                    entity.getLastTriggerAt(), entity.getLastFeedbackAt(), option).triggerAt();
        }
        if (nextTriggerAt == null) {
            // 在途 Execution 尚未反馈，由反馈链路继续推进
            return null;
        }
        if (!scheduleTriggerCheck(nextTriggerAt, now, option)) {
            return null;
        }
        if (!createExecutions(entity.getScheduleId(), Collections.singletonList(nextTriggerAt))) {
            return null;
        }
        entity.setLastTriggerAt(nextTriggerAt);
        entity.setNextTriggerAt(null);
        return new RecoveryCounters();
    }

    /**
     * CRON/FIXED_RATE：以 nextTriggerAt 为起点的未决区间，晚于 now - misfireThreshold
     * 的点按预生成窗口正常创建；更早的点为遗漏候选（见 CONTEXT.md「遗漏触发恢复」中的已决
     * 语义：开启恢复时只检查最新的 10 个原始点，SKIP 或首批 Deadline 已过的点明确丢弃，
     * 更早点随游标推进丢弃，均不物化 SKIPPED/EXPIRED 实例）。
     */
    private RecoveryCounters handleCalendarType(ScheduleEntity entity, ScheduleOption option, Instant now) {
        String scheduleId = entity.getScheduleId();
        Instant firstUndecided = entity.getNextTriggerAt();
        if (firstUndecided == null) {
            // 该类型下发布/反馈总会留下非空游标；防御性返回，不推进
            return null;
        }
        Instant windowStart = option.startInstant();
        Instant windowEnd = option.endInstant();
        if (windowEnd != null && firstUndecided.isAfter(windowEnd)) {
            // 生效窗口已结束：保持游标不动
            return null;
        }
        if (ScheduleType.FIXED_RATE == option.getType()
            && (option.getInterval() == null || option.getInterval().isNegative() || option.getInterval().isZero())) {
            // 非法间隔若继续展开会原地循环；拒绝处理，待配置修正
            log.error("[ScheduleTrigger] invalid fixed_rate interval, scheduleId:{}", scheduleId);
            return null;
        }
        Instant loadEndAt = now.plusSeconds(ScheduleConstants.EXECUTION_LOOK_AHEAD_SECONDS);
        Instant missedBoundary = now.minusSeconds(ExecutionScheduleCommandService.MISFIRE_THRESHOLD_SECONDS);

        List<Instant> toCreate = new ArrayList<>();
        RecoveryCounters counters = new RecoveryCounters();
        boolean hasMissed = firstUndecided.isBefore(missedBoundary);
        Instant lastDecided = entity.getLastTriggerAt();
        if (hasMissed) {
            // 遗漏区间 [firstUndecided, min(missedBoundary, windowEnd]) 存在；
            // upper 取开区间上界：windowEnd 本身是合法触发点，missedBoundary 时刻的点不算遗漏
            Instant upper = windowEnd != null && windowEnd.isBefore(missedBoundary)
                ? windowEnd.plusNanos(1) : missedBoundary;
            Instant newestMissed = latestPointBefore(option, firstUndecided, upper);
            if (newestMissed == null) {
                // CRON 反向计算不出更新点（表达式更早已无触发点/解析失败）：本轮放弃，游标不动
                log.error("[ScheduleTrigger] cannot locate newest missed point, scheduleId:{}", scheduleId);
                return null;
            }
            processMissedCandidates(entity, option, now, firstUndecided, newestMissed, windowStart, toCreate, counters);
            // 遗漏区间（含未检查的更早点）全部已作出决策：游标基准推进到区间内最新点
            lastDecided = newestMissed;
        }
        // 未来预生成：无遗漏时由 firstUndecided 起步；有遗漏时从遗漏区间后继点继续，
        // 刻意使用纯栅格步进而非 BasicCalculation（后者会把低于求值时刻的次点抬到 now，造成相位漂移）
        Instant point = hasMissed ? stepForward(option, lastDecided) : firstUndecided;
        while (point != null && !point.isAfter(loadEndAt)
            && (windowEnd == null || !point.isAfter(windowEnd))) {
            if (windowStart == null || !point.isBefore(windowStart)) {
                toCreate.add(point);
            }
            lastDecided = point;
            point = stepForward(option, point);
        }
        if (createExecutions(scheduleId, toCreate)) {
            entity.setLastTriggerAt(lastDecided);
            entity.setNextTriggerAt(point);
            return counters;
        }
        // 创建不可解析：无任何点落库、游标不动，也不发布恢复决策指标
        return null;
    }

    /**
     * 遗漏候选决策：恢复关闭时区间整体交由游标推进丢弃；开启时仅按原始触发时间取最接近
     * now 的 {@link ScheduleConstants#RECOVERY_POINTS_LIMIT} 个，更早点不再回看也不计数。
     * 决策结果写入 counters（由调用方在事务提交成功后统一发布），待创建点追加到 toCreate。
     */
    private void processMissedCandidates(ScheduleEntity entity, ScheduleOption option, Instant now,
                                         Instant lowerBound, Instant newestMissed,
                                         Instant windowStart, List<Instant> toCreate, RecoveryCounters counters) {
        if (!option.isRecoveryEnabled()) {
            return;
        }
        String scheduleId = entity.getScheduleId();
        // 自新到旧收集候选，再反转为时间正序处理
        List<Instant> candidates = new ArrayList<>();
        Instant candidate = newestMissed;
        while (candidate != null && candidates.size() < ScheduleConstants.RECOVERY_POINTS_LIMIT) {
            if (candidate.isBefore(lowerBound)) {
                // 已越过未决区间下界：更早点全部交由游标推进丢弃
                break;
            }
            if (windowStart != null && candidate.isBefore(windowStart)) {
                // 生效窗口之前的点不是触发点，同样是游标语义内的丢弃
                break;
            }
            candidates.add(candidate);
            candidate = previousPoint(option, candidate);
        }
        Collections.reverse(candidates);
        for (Instant triggerAt : candidates) {
            // 已存在的同点 Execution 占用本轮回看名额并视为该点已处理（幂等确认）
            if (executionEntityRepo.findByTriggerIdAndTriggerAt(scheduleId, triggerAt) != null) {
                counters.recovered++;
                continue;
            }
            if (option.getMisfirePolicy() == MisfirePolicy.SKIP) {
                // 遗漏点到领取时必然满足 misfire 阈值，SKIP 策略下只能即时错过：不物化 SKIPPED 实例
                counters.dropped++;
                continue;
            }
            Long maxStartDelayMillis = option.getMaxStartDelayMillis();
            if (maxStartDelayMillis != null
                && !triggerAt.plusMillis(maxStartDelayMillis).isAfter(now)) {
                // 首批 Job 激活截止时间已过：不物化必然 EXPIRED 的实例
                counters.dropped++;
                continue;
            }
            toCreate.add(triggerAt);
            counters.recovered++;
        }
    }

    /**
     * 按时间正序创建 Execution：与游标、指标同处外层事务（见类注释）。同一
     * (triggerId, triggerAt) 的已存在或并发竞争由 Execution 创建原语在数据库层幂等吸收：
     * 竞争不抛唯一键异常，该点视为已处理，本轮继续后续点；其他持久化错误直接上抛——
     * 外层事务回滚全部未提交的 Execution 与游标，本批对外从未可见。全部点已创建/已确认
     * 时返回 true；Trigger/Executable 解析失败返回 false（未落任何点）。
     */
    private boolean createExecutions(String scheduleId, List<Instant> triggerPoints) {
        if (triggerPoints.isEmpty()) {
            return true;
        }
        Trigger trigger = Query.query(new TriggerByIdQuery(scheduleId)).getTrigger();
        if (trigger == null) {
            log.error("[ScheduleTrigger] trigger missing, scheduleId:{}", scheduleId);
            return false;
        }
        Executable executable = Query.query(new ExecutableByIdQuery(
            trigger.executableId(), trigger.getConfig().getExecuteConfig().type()
        )).getExecutable();
        if (executable == null) {
            log.error("[ScheduleTrigger] executable missing, scheduleId:{}, executableId:{}",
                scheduleId, trigger.executableId());
            return false;
        }
        for (Instant triggerAt : triggerPoints) {
            // CREATED/EXISTING 均视为该点已决策；其他异常上抛由外层事务整批回滚
            Cmd.send(new ExecutionCreateCmd(
                trigger.getId(), TriggerType.SCHEDULE, executable, triggerAt));
        }
        return true;
    }

    /**
     * 事务成功提交后才发布本轮恢复决策计数（回调丢失/非事务环境时立即发布）：
     * 指标只反映已提交的决策，失败回滚不产生虚假的恢复/丢弃计数。
     */
    private void publishAfterCommit(RecoveryCounters counters) {
        if (counters == null || (counters.recovered == 0 && counters.dropped == 0)) {
            return;
        }
        Runnable publish = () -> {
            recoveryMetrics.recordRecovered(counters.recovered);
            recoveryMetrics.recordDropped(counters.dropped);
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                publish.run();
            }
        });
    }

    /** 本轮触发处理的恢复决策计数（同点幂等确认计入 recovered，SKIP/过期 Deadline 计入 dropped） */
    private static final class RecoveryCounters {
        private int recovered;
        private int dropped;
    }

    /**
     * 未决区间 [lowerBound, upper) 内最新的原始触发点；CRON 用 cron-utils 反向一步求解，
     * FIXED_RATE 走 lowerBound 锚定栅格的封闭算术——均不将长时间历史展开到内存。
     */
    private Instant latestPointBefore(ScheduleOption option, Instant lowerBound, Instant upper) {
        if (ScheduleType.CRON == option.getType()) {
            return CronScheduleCalculator.previousExecution(
                option.getCron(), option.getCronType(), option.getTimeZone(), upper);
        }
        Duration interval = option.getInterval();
        long steps = Duration.between(lowerBound, upper).toMillis() / interval.toMillis();
        if (!lowerBound.plusMillis(steps * interval.toMillis()).isBefore(upper)) {
            // 恰好压在开区间上界（该点不算遗漏），回退一格
            steps--;
        }
        return steps >= 0 ? lowerBound.plusMillis(steps * interval.toMillis()) : null;
    }

    /** 原始触发栅格上严格早于 point 的前一个触发点 */
    private Instant previousPoint(ScheduleOption option, Instant point) {
        if (ScheduleType.CRON == option.getType()) {
            return CronScheduleCalculator.previousExecution(
                option.getCron(), option.getCronType(), option.getTimeZone(), point);
        }
        return point.minus(option.getInterval());
    }

    /** 原始触发栅格上 point 之后的下一个触发点 */
    private Instant stepForward(ScheduleOption option, Instant point) {
        if (ScheduleType.CRON == option.getType()) {
            // CRON 计算器只用 lastTriggerAt 一项，返回严格晚于 point 的下一次日历命中
            return new BasicCalculation(point, null, option).triggerAt();
        }
        return point.plus(option.getInterval());
    }

    /** 锁定 scheduleId 所属 Bucket 行，建立所有「写 Schedule 行并触发」路径统一的 Bucket → Schedule 顺序 */
    private void lockBucketRow(String scheduleId) {
        int bucket = Cmd.send(new BucketAllotCmd(scheduleId)).getBucket();
        bucketEntityRepo.findWithLockingByBucket(bucket);
    }

    private boolean scheduleTriggerCheck(Instant nextTriggerAt, Instant now, ScheduleOption option) {
        if (nextTriggerAt.isBefore(now)
                || nextTriggerAt.isBefore(option.startInstant())) {
            return false;
        }
        if (nextTriggerAt.isAfter(option.endInstant())
                || nextTriggerAt.isAfter(now.plusSeconds(ScheduleConstants.EXECUTION_LOOK_AHEAD_SECONDS))) {
            return false;
        }
        return true;
    }

    private void invalidatePendingExecutions(String triggerId) {
        entityManager.createQuery("update ExecutionEntity set status = :status " +
                "where triggerId = :triggerId and status = :pending and deleted = false")
            .setParameter("status", ExecutionStatus.INVALID.value)
            .setParameter("pending", ExecutionStatus.PENDING.value)
            .setParameter("triggerId", triggerId)
            .executeUpdate();
    }

}