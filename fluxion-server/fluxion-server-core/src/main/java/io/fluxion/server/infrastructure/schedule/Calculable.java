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

package io.fluxion.server.infrastructure.schedule;

import java.time.LocalDateTime;

/**
 * 可计算调度的信息
 *
 * @author Devil
 * @since 2022/8/8
 */
public interface Calculable {

    /**
     * 获取调度配置
     */
    ScheduleOption scheduleOption();

    /**
     * 获取上次触发时间
     */
    LocalDateTime lastTriggerAt();

    /**
     * 获取上次调度反馈的时间
     */
    LocalDateTime lastFeedbackAt();

}
