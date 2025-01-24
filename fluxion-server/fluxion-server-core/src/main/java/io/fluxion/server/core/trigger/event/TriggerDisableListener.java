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

import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.server.infrastructure.dao.entity.TriggerEntity;
import io.fluxion.server.infrastructure.dao.repository.ScheduleTaskEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.TriggerEntityRepo;
import io.fluxion.server.core.trigger.Trigger;
import io.fluxion.server.core.trigger.TriggerConfig;
import io.fluxion.server.core.trigger.cmd.TriggerDisableCmd;
import io.fluxion.server.infrastructure.exception.ErrorCode;
import io.fluxion.server.infrastructure.exception.PlatformException;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author Devil
 */
@Component
public class TriggerDisableListener {

    @Resource
    private TriggerEntityRepo triggerEntityRepo;

    @Resource
    private ScheduleTaskEntityRepo scheduleTaskEntityRepo;

    /**
     * 触发调度等关闭
     */
    @EventHandler
    public void on(TriggerDisableCmd event) {
        TriggerEntity triggerEntity = triggerEntityRepo.findById(event.getId()).orElseThrow(
            PlatformException.supplier(ErrorCode.PARAM_ERROR, "can't find trigger by id:" + event.getId())
        );

        TriggerConfig config = JacksonUtils.toType(triggerEntity.getConfig(), TriggerConfig.class);
        switch (config.getTrigger().getType()) {
            case Trigger.Type.SCHEDULE:
                scheduleTaskEntityRepo.updateEnable(TriggerHelper.scheduleTaskId(triggerEntity.getTriggerId()), false);
                break;
        }
    }
}
