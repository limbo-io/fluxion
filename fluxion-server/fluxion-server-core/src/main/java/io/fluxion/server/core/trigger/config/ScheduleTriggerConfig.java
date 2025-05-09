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

package io.fluxion.server.core.trigger.config;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.fluxion.server.core.workflow.WorkflowConstants;
import io.fluxion.server.core.trigger.TriggerConfig;
import io.fluxion.server.core.trigger.TriggerType;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import io.fluxion.server.infrastructure.validata.ValidateSuppressInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 基于调度配置执行后续
 *
 * @author Devil
 */
@EqualsAndHashCode(callSuper = true)
@Data
@JsonTypeName(TriggerType.Val.SCHEDULE)
public class ScheduleTriggerConfig extends TriggerConfig {

    /**
     * 调度信息配置
     */
    private ScheduleOption scheduleOption;

    @Override
    public List<ValidateSuppressInfo> validate() {
        if (scheduleOption == null) {
            return Collections.singletonList(new ValidateSuppressInfo(WorkflowConstants.SCHEDULE_CONFIG_ERROR));
        }
        List<ValidateSuppressInfo> infos = new ArrayList<>();
        if (scheduleOption.getType() == null || ScheduleType.UNKNOWN == scheduleOption.getType()) {
            infos.add(new ValidateSuppressInfo(WorkflowConstants.SCHEDULE_CONFIG_ERROR));
        }
        switch (scheduleOption.getType()) {
            case CRON:
                if (StringUtils.isBlank(scheduleOption.getCron())) {
                    infos.add(new ValidateSuppressInfo(WorkflowConstants.SCHEDULE_CONFIG_ERROR));
                }
                break;
            case FIXED_RATE:
            case FIXED_DELAY:
                if (scheduleOption.getInterval() == null) {
                    infos.add(new ValidateSuppressInfo(WorkflowConstants.SCHEDULE_CONFIG_ERROR));
                }
                break;
        }
        return infos;
    }
}
