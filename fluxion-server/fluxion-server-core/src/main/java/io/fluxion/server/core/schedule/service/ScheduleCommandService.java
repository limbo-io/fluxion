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
import io.fluxion.common.utils.time.Formatters;
import io.fluxion.common.utils.time.LocalDateTimeUtils;
import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.server.core.broker.BrokerManger;
import io.fluxion.server.core.broker.BrokerNode;
import io.fluxion.server.core.schedule.Schedule;
import io.fluxion.server.core.schedule.ScheduleConstants;
import io.fluxion.server.core.schedule.ScheduleDelay;
import io.fluxion.server.core.schedule.cmd.ScheduleBrokerElectCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleCreateCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleDelayDeleteByScheduleCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleDelayLoadCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleDelaySaveCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleDeleteCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleDisableCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleEnableCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleFeedbackCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleTriggerCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleUpdateCmd;
import io.fluxion.server.core.schedule.converter.ScheduleEntityConverter;
import io.fluxion.server.core.schedule.query.ScheduleByIdQuery;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.dao.entity.ScheduleEntity;
import io.fluxion.server.infrastructure.dao.repository.ScheduleEntityRepo;
import io.fluxion.server.infrastructure.lock.DistributedLock;
import io.fluxion.server.infrastructure.schedule.BasicCalculation;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import java.time.LocalDateTime;

/**
 * @author Devil
 */
@Slf4j
@Service
public class ScheduleCommandService {

    private static final String ELECT_LOCK = "%s_schedule_elect";

    @Resource
    private ScheduleEntityRepo scheduleEntityRepo;

    @Resource
    private BrokerManger brokerManger;

    @Resource
    private DistributedLock distributedLock;

    @Resource
    private EntityManager entityManager;

    private static final LocalDateTime FIXED_DELAY_TRIGGER_LIMIT = LocalDateTimeUtils.parse("2999-12-01 00:00:00", Formatters.YMD_HMS);

    @CommandHandler
    public void handle(ScheduleCreateCmd cmd) {
        Schedule schedule = cmd.getSchedule();
        BasicCalculation calculation = new BasicCalculation(
            null, null, schedule.getOption()
        );
        schedule.setNextTriggerAt(calculation.triggerAt());
        ScheduleEntity entity = ScheduleEntityConverter.convert(cmd.getSchedule());
        scheduleEntityRepo.saveAndFlush(entity);
        // 调度
        Cmd.send(new ScheduleTriggerCmd(schedule));
    }

    @CommandHandler
    public void handle(ScheduleUpdateCmd cmd) {
        Schedule newSchedule = cmd.getSchedule();
        Schedule oldSchedule = Query.query(new ScheduleByIdQuery(newSchedule.getId())).getSchedule();
        if (oldSchedule == null) {
            return;
        }
        ScheduleEntity entity = ScheduleEntityConverter.convert(newSchedule);
        scheduleEntityRepo.saveAndFlush(entity);
        // 判断版本是否变化 变化就要先删delay(等待状态) 后创建新的并调度
        if (!newSchedule.version().equals(oldSchedule.version())) {
            Cmd.send(new ScheduleDelayDeleteByScheduleCmd(newSchedule.getId(), Lists.newArrayList(
                ScheduleDelay.Status.INIT, ScheduleDelay.Status.LOADED
            )));
            Cmd.send(new ScheduleTriggerCmd(newSchedule));
        }
    }

    @CommandHandler
    public void handle(ScheduleDeleteCmd cmd) {
        scheduleEntityRepo.deleteById(cmd.getId()); // 软删除 交由 DataCleaner 删除
        // 删除 未运行的 delay
        Cmd.send(new ScheduleDelayDeleteByScheduleCmd(cmd.getId(), Lists.newArrayList(
            ScheduleDelay.Status.INIT, ScheduleDelay.Status.LOADED
        )));
    }

    @CommandHandler
    public void handle(ScheduleEnableCmd cmd) {
        scheduleEntityRepo.updateEnable(cmd.getId(), true);
        // 直接新增 delay  由于 delay 的唯一性，已经存在的监控报错 就不调度，其它的要调度
        Schedule schedule = Query.query(new ScheduleByIdQuery(cmd.getId())).getSchedule();
        Cmd.send(new ScheduleTriggerCmd(schedule));
    }

    @CommandHandler
    public void handle(ScheduleDisableCmd cmd) {
        scheduleEntityRepo.updateEnable(cmd.getId(), false);
        // 可以不删，不然开关会比较重
    }

    @CommandHandler
    public void handle(ScheduleTriggerCmd cmd) {
        Schedule schedule = cmd.getSchedule();
        if (!schedule.isEnabled()) {
            return;
        }
        LocalDateTime now = TimeUtils.currentLocalDateTime();
        if (schedule.getNextTriggerAt().isBefore(now) || schedule.getNextTriggerAt().isBefore(schedule.getOption().getStartTime())) {
            return;
        }
        if (schedule.getNextTriggerAt().isAfter(schedule.getOption().getEndTime())
            || schedule.getNextTriggerAt().isAfter(now.plusSeconds(ScheduleConstants.LOAD_INTERVAL_SECONDS))) {
            return;
        }
        ScheduleDelay delay = new ScheduleDelay(
            new ScheduleDelay.ID(schedule.getId(), schedule.getNextTriggerAt()),
            ScheduleDelay.Status.INIT,
            schedule.getBrokerId()
        );
        // 保存延迟任务
        Cmd.send(new ScheduleDelaySaveCmd(delay));
        // 加载
        Cmd.send(new ScheduleDelayLoadCmd(delay));
        // 更新上次触发时间
        schedule.setLastTriggerAt(schedule.getNextTriggerAt());
        // 更新下次触发时间
        if (ScheduleType.FIXED_DELAY == schedule.getOption().getType()) {
            schedule.setNextTriggerAt(null); // 反馈的时候计算下次触发时间，先置空
        } else {
            BasicCalculation calculation = new BasicCalculation(
                schedule.getLastTriggerAt(), schedule.getLastFeedbackAt(), schedule.getOption()
            );
            schedule.setNextTriggerAt(calculation.triggerAt());
        }
        // 更新上次触发时间和下次触发时间
        entityManager.createQuery("update ScheduleEntity " +
                "set lastTriggerAt = :lastTriggerAt, nextTriggerAt = :nextTriggerAt " +
                "where id = :id"
            )
            .setParameter("lastTriggerAt", schedule.getLastTriggerAt())
            .setParameter("nextTriggerAt", schedule.getNextTriggerAt())
            .setParameter("id", schedule.getId())
            .executeUpdate();
    }

    @CommandHandler
    public void handle(ScheduleFeedbackCmd cmd) {
        Schedule schedule = cmd.getSchedule();
        if (ScheduleType.FIXED_DELAY != schedule.getOption().getType()) {
            return;
        }
        // 更新反馈时间 todo @d
//        Cmd.send(new ScheduleRefreshLastFeedbackCmd(
//            schedule.getId(), feedbackTime
//        ));
        // FIXED_DELAY 类型的这个时候下发后续的
//        DelayedTaskScheduler delayedTaskScheduler = BrokerContext.broker().delayedTaskScheduler();
//        delayedTaskScheduler.schedule(DelayedTaskFactory.create(
//            TriggerHelper.taskScheduleId(schedule),
//            null, schedule.getOption(),
//            delayedTask -> TriggerHelper.consumerTask(delayedTask, schedule.getId())
//        ));
    }

    @CommandHandler
    public void handle(ScheduleBrokerElectCmd cmd) {
        Schedule schedule = cmd.getSchedule();
        String lock = String.format(ELECT_LOCK, schedule.getId());
        try {
            if (!distributedLock.lock(lock, 3)) {
                return;
            }
            BrokerNode elect = brokerManger.elect(schedule.getId());
            scheduleEntityRepo.updateBrokerId(schedule.getId(), elect.id());
            distributedLock.unlock(lock);
        } catch (Exception e) {
            log.error("Schedule Elect Fail id:{}", schedule.getId(), e);
            distributedLock.unlock(lock);
        }
    }

}
