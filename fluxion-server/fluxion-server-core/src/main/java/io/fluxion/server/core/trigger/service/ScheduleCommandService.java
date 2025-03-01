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

package io.fluxion.server.core.trigger.service;

import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.server.core.broker.BrokerManger;
import io.fluxion.server.core.broker.BrokerNode;
import io.fluxion.server.core.trigger.cmd.ScheduleBrokerElectCmd;
import io.fluxion.server.core.trigger.cmd.ScheduleRefreshLastFeedbackCmd;
import io.fluxion.server.core.trigger.cmd.ScheduleRefreshLastTriggerCmd;
import io.fluxion.server.core.trigger.cmd.ScheduleSaveCmd;
import io.fluxion.server.core.trigger.converter.ScheduleEntityConverter;
import io.fluxion.server.infrastructure.dao.entity.ScheduleEntity;
import io.fluxion.server.infrastructure.dao.repository.ScheduleEntityRepo;
import io.fluxion.server.infrastructure.lock.DistributedLock;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.LockModeType;

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

    @CommandHandler
    public String handle(ScheduleSaveCmd cmd) {
        ScheduleEntity entity = ScheduleEntityConverter.convert(cmd.getSchedule());
        scheduleEntityRepo.saveAndFlush(entity);
        // todo @d 如果是首次创建，立即进行调度 否则保存下次触发时间为最近一次触发时间
        return entity.getScheduleId();
    }

    @CommandHandler
    public void handle(ScheduleBrokerElectCmd cmd) {
        String scheduleId = cmd.getScheduleId();
        String lock = String.format(ELECT_LOCK, scheduleId);
        try {
            if (!distributedLock.lock(lock, 3)) {
                return;
            }
            BrokerNode elect = brokerManger.elect(scheduleId);
            scheduleEntityRepo.updateBrokerId(scheduleId, elect.id());
            distributedLock.unlock(lock);
        } catch (Exception e) {
            log.error("Schedule Elect Fail id:{}", cmd.getScheduleId(), e);
            distributedLock.unlock(lock);
        }
    }

    @CommandHandler
    public boolean handle(ScheduleRefreshLastTriggerCmd cmd) {
        EntityTransaction transaction = entityManager.getTransaction();
        transaction.begin();

        try {
            ScheduleEntity entity = entityManager.find(ScheduleEntity.class, cmd.getScheduleId(), LockModeType.PESSIMISTIC_WRITE);
            entity.setLastTriggerAt(cmd.getLastTriggerAt());

            // 提交事务 todo 这里不用保存？？
            transaction.commit();
        } catch (Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            log.error("ScheduleRefreshNext fail cmd:{}", JacksonUtils.toJSONString(cmd), e);
        }
        return true;
    }

    @CommandHandler
    public boolean handle(ScheduleRefreshLastFeedbackCmd cmd) {
        EntityTransaction transaction = entityManager.getTransaction();
        transaction.begin();

        try {
            ScheduleEntity entity = entityManager.find(ScheduleEntity.class, cmd.getScheduleId(), LockModeType.PESSIMISTIC_WRITE);
            entity.setLastFeedbackAt(cmd.getLastFeedbackAt());

            // 提交事务 todo 这里不用保存？？
            transaction.commit();
        } catch (Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            log.error("ScheduleRefreshNext fail cmd:{}", JacksonUtils.toJSONString(cmd), e);
        }
        return true;
    }
}
