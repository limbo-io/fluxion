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

package io.fluxion.server.core.schedule.cmd;

import io.fluxion.server.infrastructure.cqrs.ICmd;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 只变更 配置信息，如果没有则创建
 *
 * @author Devil
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleSaveCmd implements ICmd<ScheduleSaveCmd.Response> {

    private String id;

    private ScheduleOption option;

    @Getter
    @AllArgsConstructor
    public static class Response {
        private String id;
    }

}
