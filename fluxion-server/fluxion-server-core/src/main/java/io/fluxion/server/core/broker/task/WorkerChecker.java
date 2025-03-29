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

package io.fluxion.server.core.broker.task;

import io.fluxion.server.core.schedule.ScheduleDelay;
import io.fluxion.server.core.schedule.ScheduleDelayConstants;
import io.fluxion.server.core.schedule.cmd.ScheduleDelayLoadCmd;
import io.fluxion.server.core.schedule.query.ScheduleDelayNextTriggerQuery;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

/**
 * 1. 加载 create 状态执行TaskRunCmd
 * 2. 加载 dispatched + running 状态比较久的 判断超时
 * todo !
 *
 * @author Devil
 */
@Slf4j
public class WorkerChecker extends CoreTask {

    public WorkerChecker() {
        super(0, ScheduleDelayConstants.LOAD_INTERVAL, ScheduleDelayConstants.LOAD_TIME_UNIT);
    }

    @Override
    public void run() {
        try {
            List<ScheduleDelay> delays = Query.query(new ScheduleDelayNextTriggerQuery(100)).getDelays();
            while (CollectionUtils.isNotEmpty(delays)) {
                for (ScheduleDelay delay : delays) {
                    Cmd.send(new ScheduleDelayLoadCmd(delay));
                }
                // 拉取后续的
                delays = Query.query(new ScheduleDelayNextTriggerQuery(100)).getDelays();
            }
        } catch (Exception e) {
            log.error("[{}] execute fail", this.getClass().getSimpleName(), e);
        }

    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.FIXED_DELAY;
    }
}
