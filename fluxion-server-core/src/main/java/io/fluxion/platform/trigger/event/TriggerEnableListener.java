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

import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.platform.dao.entity.TriggerEntity;
import io.fluxion.platform.dao.repository.ScheduledTaskEntityRepository;
import io.fluxion.platform.dao.repository.TriggerEntityRepository;
import io.fluxion.platform.trigger.Trigger;
import io.fluxion.platform.trigger.TriggerConfig;
import io.fluxion.platform.trigger.cmd.TriggerEnableCmd;
import io.fluxion.platform.exception.ErrorCode;
import io.fluxion.platform.exception.PlatformException;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author Devil
 */
@Component
public class TriggerEnableListener {

    @Resource
    private TriggerEntityRepository triggerEntityRepository;

    @Resource
    private ScheduledTaskEntityRepository scheduledTaskEntityRepository;

    /**
     * 触发调度等开启
     */
    @EventHandler
    public void on(TriggerEnableCmd event) {
        TriggerEntity triggerEntity = triggerEntityRepository.findById(event.getId()).orElseThrow(
            PlatformException.supplier(ErrorCode.PARAM_ERROR, "can't find trigger by id:" + event.getId())
        );

        TriggerConfig config = JacksonUtils.toType(triggerEntity.getConfig(), TriggerConfig.class);
        switch (config.getTrigger().getType()) {
            case Trigger.Type.SCHEDULE:
                scheduledTaskEntityRepository.updateEnable(TriggerHelper.scheduleTaskId(triggerEntity.getTriggerId()), true);
                break;
        }

    }

}
