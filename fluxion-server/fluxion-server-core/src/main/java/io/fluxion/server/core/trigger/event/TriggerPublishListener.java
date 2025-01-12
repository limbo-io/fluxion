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

package io.fluxion.server.core.trigger.event;

import io.fluxion.common.utils.MD5Utils;
import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.server.infrastructure.dao.entity.ScheduleTaskEntity;
import io.fluxion.server.infrastructure.dao.entity.TriggerEntity;
import io.fluxion.server.infrastructure.dao.repository.ScheduledTaskEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.TriggerEntityRepo;
import io.fluxion.server.infrastructure.exception.ErrorCode;
import io.fluxion.server.infrastructure.exception.PlatformException;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;
import io.fluxion.server.core.trigger.ScheduleTrigger;
import io.fluxion.server.core.trigger.Trigger;
import io.fluxion.server.core.trigger.TriggerConfig;
import io.fluxion.server.core.trigger.cmd.TriggerPublishCmd;
import org.apache.commons.lang3.StringUtils;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author Devil
 */
@Component
public class TriggerPublishListener {

    @Resource
    private TriggerEntityRepo triggerEntityRepo;

    @Resource
    private ScheduledTaskEntityRepo scheduledTaskEntityRepo;

    /**
     * 更新 task等
     */
    @EventHandler
    public void on(TriggerPublishCmd event) {
        TriggerConfig config = event.getConfig();

        TriggerEntity triggerEntity = triggerEntityRepo.findById(event.getId()).orElseThrow(
            PlatformException.supplier(ErrorCode.PARAM_ERROR, "can't find trigger by id:" + event.getId())
        );

        switch (config.getTrigger().getType()) {
            case Trigger.Type.SCHEDULE:
                handleSchedule(triggerEntity, (ScheduleTrigger) config.getTrigger());
                break;
        }

    }

    /**
     * 更新触发器对应的 调度任务
     * 目前一个 trigger 对应 一个 ScheduleTask
     */
    private void handleSchedule(TriggerEntity triggerEntity, ScheduleTrigger schedule) {
        ScheduleOption scheduleOption = schedule.getScheduleOption();
        String scheduleTaskId = TriggerHelper.scheduleTaskId(triggerEntity.getTriggerId());
        ScheduleTaskEntity scheduleTaskEntity = scheduledTaskEntityRepo.findById(scheduleTaskId).orElse(new ScheduleTaskEntity());
        scheduleTaskEntity.setRefId(triggerEntity.getRefId());
        scheduleTaskEntity.setRefType(triggerEntity.getRefType());
        scheduleTaskEntity.setScheduleType(scheduleOption.getScheduleType().type);
        scheduleTaskEntity.setScheduleStartAt(scheduleOption.getScheduleStartAt());
        scheduleTaskEntity.setScheduleEndAt(scheduleOption.getScheduleEndAt());
        scheduleTaskEntity.setScheduleDelay(scheduleOption.getScheduleDelay().getSeconds());
        scheduleTaskEntity.setScheduleInterval(scheduleOption.getScheduleInterval().getSeconds());
        scheduleTaskEntity.setScheduleCron(scheduleOption.getScheduleCron());
        scheduleTaskEntity.setScheduleCronType(scheduleOption.getScheduleCronType());
        // 使用配置信息作为version 后续即可判断配置是否变化
        String version = MD5Utils.md5(JacksonUtils.toJSONString(scheduleOption));
        if (StringUtils.isBlank(scheduleTaskEntity.getScheduleTaskId())) {
            scheduleTaskEntity.setScheduleTaskId(scheduleTaskId);
            scheduleTaskEntity.setEnabled(false);
            scheduleTaskEntity.setVersion(version);
            scheduledTaskEntityRepo.saveAndFlush(scheduleTaskEntity);
        } else if (!version.equals(scheduleTaskEntity.getVersion())) {
            scheduleTaskEntity.setVersion(version);
            scheduledTaskEntityRepo.saveAndFlush(scheduleTaskEntity);
        }

        // todo 如果是首次创建，立即进行调度 否则保存下次触发时间为最近一次触发时间
    }

}
