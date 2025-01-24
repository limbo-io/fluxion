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

package io.fluxion.server.start.component;

import io.fluxion.common.utils.time.Formatters;
import io.fluxion.common.utils.time.LocalDateTimeUtils;
import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.server.core.cluster.ClusterContext;
import io.fluxion.server.core.execution.Execution;
import io.fluxion.server.core.execution.cmd.ExecutionCreateCmd;
import io.fluxion.server.core.execution.cmd.ExecutionRunCmd;
import io.fluxion.server.core.schedule.cmd.DelayTaskSubmitCmd;
import io.fluxion.server.core.schedule.cmd.ScheduledTaskSubmitCmd;
import io.fluxion.server.core.trigger.TriggerRefType;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.dao.entity.ScheduleTaskEntity;
import io.fluxion.server.infrastructure.dao.repository.ScheduleTaskEntityRepo;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import io.fluxion.server.infrastructure.schedule.task.AbstractTask;
import io.fluxion.server.infrastructure.schedule.task.DelayTaskFactory;
import io.fluxion.server.infrastructure.schedule.task.ScheduledTaskFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;

/**
 * @author Devil
 */
@Slf4j
@Component
public class ScheduledTaskLoader implements InitializingBean {

    @Resource
    private ScheduleTaskEntityRepo scheduleTaskEntityRepo;

    /**
     * 间隔 秒
     */
    private final int interval = 1;

    @Override
    public void afterPropertiesSet() {
        new Timer().schedule(new InnerTask(), 0, Duration.ofSeconds(interval).toMillis());
    }

    private class InnerTask extends TimerTask {

        private LocalDateTime loadTimePoint = LocalDateTimeUtils.parse("2000-01-01 00:00:00", Formatters.YMD_HMS);

        @Override
        public void run() {
            try {
                LocalDateTime now = TimeUtils.currentLocalDateTime();
                List<ScheduleTaskEntity> entities = scheduleTaskEntityRepo.loadByUpdated(
                    ClusterContext.currentNodeId(),
                    loadTimePoint.plusSeconds(-interval), // 防止部分延迟导致变更丢失
                    now

                );
                loadTimePoint = now;

                if (CollectionUtils.isEmpty(entities)) {
                    return;
                }
                for (ScheduleTaskEntity entity : entities) {
                    if (!needSchedule(entity)) {
                        continue;
                    }
                    String scheduleId = scheduleId(entity);
                    ScheduleOption scheduleOption = toOption(entity);
                    // 移除老的，调度新的
                    switch (ScheduleType.parse(entity.getScheduleType())) {
                        case CRON:
                        case FIXED_RATE:
                            Cmd.send(new ScheduledTaskSubmitCmd(ScheduledTaskFactory.task(
                                scheduleId,
                                entity.getLatelyTriggerAt(),
                                entity.getLatelyFeedbackAt(),
                                scheduleOption,
                                consumer(entity.getScheduleTaskId())
                            )));
                            break;
                        case FIXED_DELAY:
                            Cmd.send(new DelayTaskSubmitCmd(DelayTaskFactory.create(
                                scheduleId,
                                entity.getLatelyTriggerAt(),
                                entity.getLatelyFeedbackAt(),
                                scheduleOption,
                                consumer(entity.getScheduleTaskId())
                            )));
                            break;
                    }
                }
            } catch (Exception e) {
                log.error("[{}] execute fail", this.getClass().getSimpleName(), e);
            }
        }

        private <T extends AbstractTask> Consumer<T> consumer(String scheduleTaskId) {
            return task -> {
                // 移除不需要调度的
                ScheduleTaskEntity entity = scheduleTaskEntityRepo.findById(scheduleTaskId).orElse(null);
                if (!needSchedule(entity)) {
                    task.stop();
                    return;
                }
                // 版本变化了老的可以不用执行
                if (!task.id().equals(scheduleId(entity))) {
                    task.stop();
                    return;
                }
                Execution execution = Cmd.send(new ExecutionCreateCmd(
                    entity.getRefId(),
                    TriggerRefType.parse(entity.getRefType()),
                    task.triggerAt()
                )).getExecution();
                Cmd.send(new ExecutionRunCmd(execution));
            };
        }

        /**
         * 是否需要调度
         * 开始结束周期校验已经在 ScheduledTaskScheduler 统一处理
         */
        private boolean needSchedule(ScheduleTaskEntity entity) {
            return entity != null && entity.isEnabled() && entity.isDeleted();
        }

        private String scheduleId(ScheduleTaskEntity entity) {
            // entity = trigger 所以不会变，这里使用version判断是否有版本变动
            return "st_" + entity.getRefType() + "-" + entity.getRefId() + "-" + entity.getVersion();
        }

        private ScheduleOption toOption(ScheduleTaskEntity entity) {
            return new ScheduleOption(
                ScheduleType.parse(entity.getScheduleType()),
                entity.getScheduleStartAt(),
                entity.getScheduleEndAt(),
                Duration.ofMillis(entity.getScheduleDelay()),
                Duration.ofMillis(entity.getScheduleInterval()),
                entity.getScheduleCron(),
                entity.getScheduleCronType()
            );
        }
    }
}
