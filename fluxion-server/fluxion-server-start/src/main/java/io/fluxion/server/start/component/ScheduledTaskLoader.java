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
import io.fluxion.core.cluster.ClusterContext;
import io.fluxion.core.cqrs.Cmd;
import io.fluxion.core.dao.entity.ScheduleTaskEntity;
import io.fluxion.core.dao.repository.ScheduledTaskEntityRepo;
import io.fluxion.core.flow.cmd.FlowExecuteCmd;
import io.fluxion.core.schedule.ScheduleOption;
import io.fluxion.core.schedule.ScheduleType;
import io.fluxion.core.schedule.scheduler.DelayTaskScheduler;
import io.fluxion.core.schedule.scheduler.ScheduledTaskScheduler;
import io.fluxion.core.schedule.task.ScheduledTask;
import io.fluxion.core.trigger.Trigger;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;

/**
 * @author Devil
 * @date 2025/1/9 15:48
 */
@Slf4j
@Component
public class ScheduledTaskLoader implements InitializingBean {

    @Resource
    private ScheduledTaskScheduler scheduleTaskscheduler;
    @Resource
    private DelayTaskScheduler delayTaskScheduler;
    @Resource
    private ScheduledTaskEntityRepo scheduledTaskEntityRepo;

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
                List<ScheduleTaskEntity> entities = scheduledTaskEntityRepo.loadByUpdated(
                    ClusterContext.currentNodeId(),
                    loadTimePoint.plusSeconds(-interval) // 防止部分延迟导致变更丢失
                );
                loadTimePoint = TimeUtils.currentLocalDateTime();

                if (CollectionUtils.isEmpty(entities)) {
                    return;
                }
                for (ScheduleTaskEntity entity : entities) {
                    ScheduledTask task = new ScheduledTask(
                        scheduleTaskId(entity),
                        entity.getLatelyTriggerAt(),
                        entity.getLatelyFeedbackAt(),
                        toOption(entity),
                        t -> {
                            // todo 创建execution 下发任务
                            // todo 判断是否由当前节点触发
                            switch (entity.getRefType()) {
                                case Trigger.RefType.FLOW:
                                    Cmd.send(new FlowExecuteCmd(entity.getRefId()));
                                    break;
                                case Trigger.RefType.EXECUTOR:
                                    break;
                            }
                        }
                    );
                    if (Objects.equals(ScheduleType.FIXED_DELAY.type, entity.getScheduleType())) {
                        // 下次触发
                        delayTaskScheduler.schedule(task);
                    } else if (Objects.equals(ScheduleType.FIXED_RATE.type, entity.getScheduleType())
                        || Objects.equals(ScheduleType.CRON.type, entity.getScheduleType())) {
                        // 调度新的
                        scheduleTaskscheduler.schedule(task);
                    }
                }
            } catch (Exception e) {
                log.error("[{}] execute fail", this.getClass().getSimpleName(), e);
            }
        }
    }

    private String scheduleTaskId(ScheduleTaskEntity entity) {
        // entity = trigger 所以不会变，这里使用version判断是否有版本变动
        return entity.getRefType() + "-" + entity.getRefId() + "-" + entity.getVersion();
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
