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

package io.fluxion.platform.trigger.event;

import io.fluxion.common.utils.MD5Utils;
import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.platform.dao.entity.ScheduleTaskEntity;
import io.fluxion.platform.dao.entity.TriggerEntity;
import io.fluxion.platform.dao.repository.ScheduledTaskEntityRepository;
import io.fluxion.platform.dao.repository.TriggerEntityRepository;
import io.fluxion.platform.exception.ErrorCode;
import io.fluxion.platform.exception.PlatformException;
import io.fluxion.platform.schedule.ScheduleOption;
import io.fluxion.platform.trigger.ScheduleTrigger;
import io.fluxion.platform.trigger.Trigger;
import io.fluxion.platform.trigger.TriggerConfig;
import io.fluxion.platform.trigger.cmd.TriggerPublishCmd;
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
    private TriggerEntityRepository triggerEntityRepository;

    @Resource
    private ScheduledTaskEntityRepository scheduledTaskEntityRepository;

    /**
     * 更新 task等
     */
    @EventHandler
    public void on(TriggerPublishCmd event) {
        TriggerConfig config = event.getConfig();

        TriggerEntity triggerEntity = triggerEntityRepository.findById(event.getId()).orElseThrow(
            PlatformException.supplier(ErrorCode.PARAM_ERROR, "can't find trigger by id:" + event.getId())
        );

        switch (config.getTrigger().getType()) {
            case Trigger.Type.SCHEDULE:
                handleSchedule(triggerEntity, (ScheduleTrigger) config.getTrigger());
                break;
        }

    }

    private void handleSchedule(TriggerEntity triggerEntity, ScheduleTrigger schedule) {
        ScheduleOption scheduleOption = schedule.getScheduleOption();
        String scheduleTaskId = TriggerHelper.scheduleTaskId(triggerEntity.getTriggerId());
        ScheduleTaskEntity scheduleTaskEntity = scheduledTaskEntityRepository.findById(scheduleTaskId).orElse(new ScheduleTaskEntity());
        scheduleTaskEntity.setRefId(triggerEntity.getRefId());
        scheduleTaskEntity.setRefType(triggerEntity.getRefType());
        scheduleTaskEntity.setScheduleType(scheduleOption.getScheduleType().type);
        scheduleTaskEntity.setScheduleStartAt(scheduleOption.getScheduleStartAt());
        scheduleTaskEntity.setScheduleEndAt(scheduleOption.getScheduleEndAt());
        scheduleTaskEntity.setScheduleDelay(scheduleOption.getScheduleDelay().getSeconds());
        scheduleTaskEntity.setScheduleInterval(scheduleOption.getScheduleInterval().getSeconds());
        scheduleTaskEntity.setScheduleCron(scheduleOption.getScheduleCron());
        scheduleTaskEntity.setScheduleCronType(scheduleOption.getScheduleCronType());

        String version = MD5Utils.md5(JacksonUtils.toJSONString(scheduleOption));
        if (StringUtils.isBlank(scheduleTaskEntity.getScheduleTaskId())) {
            scheduleTaskEntity.setScheduleTaskId(scheduleTaskId);
            scheduleTaskEntity.setEnabled(false);
            scheduleTaskEntity.setVersion(version);
            scheduledTaskEntityRepository.saveAndFlush(scheduleTaskEntity);
        } else if (!version.equals(scheduleTaskEntity.getVersion())) {
            scheduleTaskEntity.setVersion(version);
            scheduledTaskEntityRepository.saveAndFlush(scheduleTaskEntity);
        }
    }

}
