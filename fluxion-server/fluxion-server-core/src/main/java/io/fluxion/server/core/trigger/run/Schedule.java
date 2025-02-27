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

package io.fluxion.server.core.trigger.run;

import io.fluxion.server.core.trigger.TriggerRefType;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author Devil
 */
@Data
public class Schedule {

    private String id;

    /**
     * 调度关联数据
     */
    private String refId;

    /**
     * 调度关联类型
     * @see TriggerRefType
     */
    private TriggerRefType refType;

    /**
     * 用来判断 调度配置是否有变化
     */
    private String version;

    /**
     * 分配的节点
     */
    private String brokerAddress;

    private ScheduleType scheduleType;

    private ScheduleOption scheduleOption;

    /**
     * 上次触发时间
     */
    private LocalDateTime lastTriggerAt;

    /**
     * 上次回调时间
     */
    private LocalDateTime lastFeedbackAt;

    private boolean enabled;
}
