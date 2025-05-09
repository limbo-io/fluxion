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

import com.google.common.collect.Lists;
import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.server.core.broker.cmd.BucketAllotCmd;
import io.fluxion.server.core.schedule.Schedule;
import io.fluxion.server.core.schedule.ScheduleConstants;
import io.fluxion.server.core.schedule.ScheduleDelay;
import io.fluxion.server.core.schedule.cmd.ScheduleDelayDeleteByScheduleCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleDelaysCreateCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleDelaysLoadCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleDeleteCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleDisableCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleEnableCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleFeedbackCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleSaveCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleTriggerCmd;
import io.fluxion.server.core.schedule.converter.ScheduleEntityConverter;
import io.fluxion.server.core.schedule.query.ScheduleByIdQuery;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.dao.entity.ScheduleEntity;
import io.fluxion.server.infrastructure.dao.repository.ScheduleEntityRepo;
import io.fluxion.server.infrastructure.schedule.BasicCalculation;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Devil
 */
@Slf4j
@Service
public class ScheduleCommandService {

    @Resource
    private ScheduleEntityRepo scheduleEntityRepo;

    @Resource
    private EntityManager entityManager;

    @Transactional
    @CommandHandler
    public void handle(ScheduleSaveCmd cmd) {
        Schedule newSchedule = new Schedule();
        newSchedule.setId(cmd.getId());
        newSchedule.setOption(cmd.getOption());

        Schedule oldSchedule = Query.query(new ScheduleByIdQuery(cmd.getId())).getSchedule();
        // 触发时间设置
        if (oldSchedule == null) {
            BasicCalculation calculation = new BasicCalculation(
                null, null, newSchedule.getOption()
            );
            newSchedule.setNextTriggerAt(calculation.triggerAt());

        }
        // bucket分配
        ScheduleEntity entity = ScheduleEntityConverter.convert(newSchedule);
        if (oldSchedule == null) {
            int bucket = Cmd.send(new BucketAllotCmd(entity.getScheduleId())).getBucket();
            entity.setBucket(bucket);
        }

        scheduleEntityRepo.saveAndFlush(entity);
        // 判断版本是否变化 变化就要先删delay(等待状态) 后创建新的并调度
        if (oldSchedule == null || !newSchedule.version().equals(oldSchedule.version())) {
            Cmd.send(new ScheduleDelayDeleteByScheduleCmd(newSchedule.getId(), Lists.newArrayList(
                ScheduleDelay.Status.INIT
            )));
            Cmd.send(new ScheduleTriggerCmd(newSchedule));
        }
    }

    @Transactional
    @CommandHandler
    public void handle(ScheduleDeleteCmd cmd) {
        scheduleEntityRepo.deleteById(cmd.getId()); // 软删除 交由 DataCleaner 删除
        // 删除 未运行的 delay
        Cmd.send(new ScheduleDelayDeleteByScheduleCmd(cmd.getId(), Lists.newArrayList(
            ScheduleDelay.Status.INIT
        )));
    }

    @Transactional
    @CommandHandler
    public void handle(ScheduleEnableCmd cmd) {
        scheduleEntityRepo.updateEnable(cmd.getId(), true);
        // 直接新增 delay  由于 delay 的唯一性，已经存在的监控报错 就不调度，其它的要调度
        Schedule schedule = Query.query(new ScheduleByIdQuery(cmd.getId())).getSchedule();
        Cmd.send(new ScheduleTriggerCmd(schedule));
    }

    @Transactional
    @CommandHandler
    public void handle(ScheduleDisableCmd cmd) {
        scheduleEntityRepo.updateEnable(cmd.getId(), false);
        // 可以不删，不然开关会比较重
    }

    @Transactional
    @CommandHandler
    public void handle(ScheduleTriggerCmd cmd) {
        Schedule schedule = cmd.getSchedule();
        if (!schedule.isEnabled()) {
            return;
        }
        LocalDateTime now = TimeUtils.currentLocalDateTime();
        // 创建一批 ScheduleDelay
        List<ScheduleDelay> delays = new ArrayList<>();
        LocalDateTime lastTriggerAt = schedule.getLastTriggerAt();
        LocalDateTime nextTriggerAt = schedule.getNextTriggerAt();
        if (nextTriggerAt.isBefore(now)) {
            nextTriggerAt = new BasicCalculation(
                lastTriggerAt, lastTriggerAt, schedule.getOption()
            ).triggerAt();
        }
        if (ScheduleType.FIXED_DELAY == schedule.getOption().getType()) {
            // FIXED_DELAY 只创建一个
            if (!scheduleTriggerCheck(nextTriggerAt, now, schedule.getOption())) {
                return;
            }
            ScheduleDelay delay = new ScheduleDelay(
                new ScheduleDelay.ID(schedule.getId(), nextTriggerAt),

                ScheduleDelay.Status.INIT
            );
            delays.add(delay);
            lastTriggerAt = nextTriggerAt; // 更新上次触发时间
            nextTriggerAt = null; // 反馈的时候计算下次触发时间，先置空
        } else {
            // CRON FIXED_RATE 创建后续多个
            while (scheduleTriggerCheck(nextTriggerAt, now, schedule.getOption())) {
                ScheduleDelay delay = new ScheduleDelay(
                    new ScheduleDelay.ID(schedule.getId(), nextTriggerAt),
                    ScheduleDelay.Status.INIT
                );
                delays.add(delay);
                lastTriggerAt = nextTriggerAt; // 更新上次触发时间
                BasicCalculation calculation = new BasicCalculation(
                    lastTriggerAt, lastTriggerAt, schedule.getOption()
                );
                nextTriggerAt = calculation.triggerAt();
            }
        }

        // 保存延迟任务
        Cmd.send(new ScheduleDelaysCreateCmd(delays));
        // 加载
        Cmd.send(new ScheduleDelaysLoadCmd(delays));

        // 更新上次触发时间和下次触发时间
        entityManager.createQuery("update ScheduleEntity " +
                "set lastTriggerAt = :lastTriggerAt, nextTriggerAt = :nextTriggerAt " +
                "where id = :id"
            )
            .setParameter("lastTriggerAt", lastTriggerAt)
            .setParameter("nextTriggerAt", nextTriggerAt)
            .setParameter("id", schedule.getId())
            .executeUpdate();
    }

    private boolean scheduleTriggerCheck(LocalDateTime nextTriggerAt, LocalDateTime now, ScheduleOption option) {
        if (nextTriggerAt.isBefore(now)
            || nextTriggerAt.isBefore(option.getStartTime())) {
            return false;
        }
        if (nextTriggerAt.isAfter(option.getEndTime())
            || nextTriggerAt.isAfter(now.plusSeconds(ScheduleConstants.LOAD_INTERVAL_SECONDS))) {
            return false;
        }
        return true;
    }

    @Transactional
    @CommandHandler
    public void handle(ScheduleFeedbackCmd cmd) {
        Schedule schedule = cmd.getSchedule();
        LocalDateTime now = TimeUtils.currentLocalDateTime();
        int rows = entityManager.createQuery("update ScheduleEntity " +
                " set lastFeedbackAt = :lastFeedbackAt " +
                " where id = :id"
            )
            .setParameter("lastFeedbackAt", now)
            .setParameter("id", schedule.getId())
            .executeUpdate();
        if (rows <= 0 || ScheduleType.FIXED_DELAY != schedule.getOption().getType()) {
            return;
        }
        // 触发下次调度
        BasicCalculation calculation = new BasicCalculation(
            schedule.getLastTriggerAt(), schedule.getLastFeedbackAt(), schedule.getOption()
        );
        schedule.setNextTriggerAt(calculation.triggerAt());
        Cmd.send(new ScheduleTriggerCmd(schedule));
    }

}
