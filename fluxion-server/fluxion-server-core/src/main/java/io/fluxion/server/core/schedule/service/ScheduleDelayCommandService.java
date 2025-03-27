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
import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.broker.cmd.BucketAllotCmd;
import io.fluxion.server.core.broker.query.BucketsByBrokerQuery;
import io.fluxion.server.core.execution.Executable;
import io.fluxion.server.core.execution.Execution;
import io.fluxion.server.core.execution.cmd.ExecutionCreateCmd;
import io.fluxion.server.core.execution.query.ExecutableByIdQuery;
import io.fluxion.server.core.schedule.ScheduleDelay;
import io.fluxion.server.core.schedule.cmd.ScheduleDelayCreateCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleDelayDeleteByScheduleCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleDelayLoadCmd;
import io.fluxion.server.core.schedule.converter.ScheduleDelayEntityConverter;
import io.fluxion.server.core.trigger.Trigger;
import io.fluxion.server.core.trigger.TriggerType;
import io.fluxion.server.core.trigger.query.TriggerByIdQuery;
import io.fluxion.server.infrastructure.concurrent.LoggingTask;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.dao.entity.ScheduleDelayEntity;
import io.fluxion.server.infrastructure.dao.repository.ScheduleDelayEntityRepo;
import io.fluxion.server.infrastructure.dao.tx.TransactionService;
import io.fluxion.server.infrastructure.schedule.schedule.DelayedTaskScheduler;
import io.fluxion.server.infrastructure.schedule.task.DelayedTask;
import io.fluxion.server.infrastructure.schedule.task.DelayedTaskFactory;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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

    @Resource
    private TransactionService transactionService;

    @CommandHandler
    public void handle(ScheduleDelayCreateCmd cmd) {
        ScheduleDelay delay = cmd.getDelay();
        ScheduleDelayEntity entity = ScheduleDelayEntityConverter.convert(delay);
        int bucket = Cmd.send(new BucketAllotCmd(delay.id())).getBucket();
        entity.setBucket(bucket);
        scheduleDelayEntityRepo.saveAndFlush(entity);
    }

    @CommandHandler
    public void handle(ScheduleDelayLoadCmd cmd) {
        ScheduleDelay delay = cmd.getDelay();
        if (ScheduleDelay.Status.INIT != delay.getStatus()) {
            return;
        }
        delay.status(ScheduleDelay.Status.LOADED);
        // 更新状态
        if (!changeDelayStatus(delay.getId(), ScheduleDelay.Status.INIT, ScheduleDelay.Status.LOADED)) {
            return;
        }
        // 加载到内存
        String scheduleId = delay.getId().getScheduleId();
        DelayedTaskScheduler delayedTaskScheduler = BrokerContext.broker().delayedTaskScheduler();
        delayedTaskScheduler.schedule(DelayedTaskFactory.create(
            delay.id(),
            delay.getId().getTriggerAt(),
            consumer(scheduleId, delay.getId())
        ));
    }

    private Consumer<DelayedTask> consumer(String scheduleId, ScheduleDelay.ID delayId) {
        return task -> {
            // 移除不需要调度的
            Trigger trigger = Query.query(new TriggerByIdQuery(scheduleId)).getTrigger();
            if (!trigger.isEnabled()) {
                changeDelayStatus(delayId, ScheduleDelay.Status.LOADED, ScheduleDelay.Status.INVALID);
                task.stop();
                log.info("Trigger is not enabled id:{}", scheduleId);
                return;
            }
            // 非当前节点的，可能重新分配给其他了
            ScheduleDelayEntity entity = scheduleDelayEntityRepo.findById(ScheduleDelayEntityConverter.convert(delayId)).get();
            String brokerId = BrokerContext.broker().id();
            List<Integer> buckets = Query.query(new BucketsByBrokerQuery(brokerId)).getBuckets();
            if (!buckets.contains(entity.getBucket())
                || !changeDelayStatus(delayId, ScheduleDelay.Status.LOADED, ScheduleDelay.Status.RUNNING)) {
                task.stop();
                log.info("ScheduleDelay is not schedule by current broker scheduleId:{} brokerId:{} bucket:{}",
                    scheduleId, brokerId, entity.getBucket()
                );
                return;
            }
            try {
                Executable executable = Query.query(new ExecutableByIdQuery(
                    trigger.getId(), trigger.getConfig().getExecuteConfig().type()
                )).getExecutable();
                // 创建执行记录
                Execution execution = Cmd.send(new ExecutionCreateCmd(
                    trigger.getId(),
                    TriggerType.SCHEDULE,
                    executable,
                    task.triggerAt()
                )).getExecution();
                // 异步执行
                CommonThreadPool.IO.submit(new LoggingTask(execution::execute));
                changeDelayStatus(delayId, ScheduleDelay.Status.RUNNING, ScheduleDelay.Status.SUCCEED);
            } catch (Exception e) {
                log.error("ScheduleDelay run error id:{}", JacksonUtils.toJSONString(delayId), e);
                changeDelayStatus(delayId, ScheduleDelay.Status.RUNNING, ScheduleDelay.Status.FAILED);
            }
        };
    }

    private boolean changeDelayStatus(ScheduleDelay.ID delayId, ScheduleDelay.Status oldStatus, ScheduleDelay.Status newStatus) {
        return transactionService.transactional(() -> entityManager.createQuery(
                "update ScheduleDelayEntity " +
                    "set status = :newStatus " +
                    "where id = :id and status = :oldStatus"
            )
            .setParameter("newStatus", newStatus.value)
            .setParameter("oldStatus", oldStatus.value)
            .setParameter("id", ScheduleDelayEntityConverter.convert(delayId))
            .executeUpdate() > 0);
    }

    @CommandHandler
    public void handle(ScheduleDelayDeleteByScheduleCmd cmd) {
        entityManager.createQuery("update ScheduleDelayEntity " +
                "set deleted = :deleted " +
                "where id.scheduleId = :scheduleId and status in :statuses"
            )
            .setParameter("statuses", cmd.getStatuses().stream().map(s -> s.value).collect(Collectors.toList()))
            .setParameter("deleted", true)
            .setParameter("scheduleId", cmd.getScheduleId())
            .executeUpdate();
    }
}
