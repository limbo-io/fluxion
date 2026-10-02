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

import io.limbo.cqrs.core.commandhandling.VoidCommand;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 触发调度处理：处理者在自身事务内依次锁定 Bucket 行、校验当前 Broker 为
 * Bucket Owner、再锁定 Schedule 行并按库内最新配置与游标推进（见 CONTEXT.md
 * 「Schedule 处理所有权」）。命令只携带 scheduleId，禁止携带 Schedule 快照参与计算，
 * 从接口上杜绝 Bucket 交接或并发入口用旧快照覆盖游标。
 *
 * @author Devil
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleTriggerCmd implements VoidCommand {

    private String scheduleId;

}