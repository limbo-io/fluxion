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

import io.fluxion.common.thread.CommonThreadPool;
import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.execution.Execution;
import io.fluxion.server.core.execution.cmd.ExecutionCreateCmd;
import io.fluxion.server.core.schedule.ScheduleDelay;
import io.fluxion.server.core.schedule.cmd.ScheduleDelayLoadCmd;
import io.fluxion.server.core.schedule.converter.ScheduleDelayEntityConverter;
import io.fluxion.server.core.trigger.Trigger;
import io.fluxion.server.core.trigger.TriggerType;
import io.fluxion.server.core.trigger.query.TriggerByIdQuery;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.dao.repository.ScheduleDelayEntityRepo;
import io.fluxion.server.infrastructure.schedule.schedule.DelayedTaskScheduler;
import io.fluxion.server.infrastructure.schedule.task.DelayedTaskFactory;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import java.util.Objects;

/**
 * @author Devil
 */
@Slf4j
@Service
public class ScheduleDelayCommandService {

    @Resource
    private EntityManager entityManager;

    @Resource
    private ScheduleDelayEntityRepo scheduleDelayEntityRepo;

    @CommandHandler
    public void handle(ScheduleDelayLoadCmd cmd) {
        ScheduleDelay delay = cmd.getDelay();
        if (ScheduleDelay.Status.INIT != delay.getStatus()) {
            return;
        }
        delay.status(ScheduleDelay.Status.LOADED);
        // 更新状态
        int rows = entityManager.createQuery("update ScheduleDelayEntity " +
                "set status = :newStatus " +
                "where id = :id and status = :oldStatus"
            )
            .setParameter("newStatus", ScheduleDelay.Status.LOADED.value)
            .setParameter("oldStatus", ScheduleDelay.Status.INIT.value)
            .setParameter("id", ScheduleDelayEntityConverter.convert(delay.getId()))
            .executeUpdate();
        if (rows <= 0) {
            return;
        }
        // 加载到内存
        String scheduleId = delay.getId().getScheduleId();
        DelayedTaskScheduler delayedTaskScheduler = BrokerContext.broker().delayedTaskScheduler();
        delayedTaskScheduler.schedule(DelayedTaskFactory.create(
            delay.id(),
            delay.getId().getTriggerAt(),
            task -> {
                // 移除不需要调度的
                Trigger trigger = Query.query(new TriggerByIdQuery(scheduleId)).getTrigger();
                if (!trigger.isEnabled()) {
                    log.info("Trigger is not enabled id:{}", scheduleId);
                    task.stop();
                    return;
                }
                // 非当前节点的，可能重新分配给其他了
                if (!Objects.equals(BrokerContext.broker().id(), delay.getBrokerId())) {
                    log.info("ScheduleDelay is not schedule by current broker scheduleId:{} brokerId:{} currentBrokerId:{}",
                        scheduleId, delay.getBrokerId(), BrokerContext.broker().id()
                    );
                    task.stop();
                    return;
                }
                // 创建执行记录
                Execution execution = Cmd.send(new ExecutionCreateCmd(
                    trigger.getId(),
                    TriggerType.SCHEDULE,
                    trigger.getExecuteConfig(),
                    task.triggerAt()
                )).getExecution();
                // 异步执行
                CommonThreadPool.IO.submit(execution::execute);
            }
        ));
    }

}
