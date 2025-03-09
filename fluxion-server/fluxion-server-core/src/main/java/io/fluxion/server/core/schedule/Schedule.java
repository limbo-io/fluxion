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

package io.fluxion.server.core.schedule;

import io.fluxion.common.utils.MD5Utils;
import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author Devil
 */
@Data
public class Schedule {

    private String id;

    private ScheduleOption option;

    /**
     * 分配的节点
     */
    private String brokerId;

    /**
     * 上次触发时间
     */
    private LocalDateTime lastTriggerAt;

    /**
     * 上次回调时间
     */
    private LocalDateTime lastFeedbackAt;

    /**
     * 下次触发时间
     */
    private LocalDateTime nextTriggerAt;

    private boolean enabled;

    /**
     * 用来判断 调度配置是否有变化
     */
    public String version() {
        return MD5Utils.md5(JacksonUtils.toJSONString(option));
    }

}
