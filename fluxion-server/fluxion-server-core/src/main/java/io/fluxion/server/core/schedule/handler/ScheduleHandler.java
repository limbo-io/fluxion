/*
 * Copyright 2024-2030 fluxion-io Team (https://github.com/fluxion-io).
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

package io.fluxion.server.core.schedule.handler;

import io.fluxion.server.core.schedule.cmd.DelayTaskSubmitCmd;
import io.fluxion.server.core.schedule.cmd.ScheduledTaskSubmitCmd;
import io.fluxion.server.infrastructure.schedule.scheduler.DelayTaskScheduler;
import io.fluxion.server.infrastructure.schedule.scheduler.ScheduledTaskScheduler;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author Devil
 */
@Component
public class ScheduleHandler {

    @Resource
    private DelayTaskScheduler delayTaskScheduler;
    @Resource
    private ScheduledTaskScheduler scheduleTaskscheduler;

    @CommandHandler
    public void handle(ScheduledTaskSubmitCmd cmd) {
        scheduleTaskscheduler.schedule(cmd.getTask());
    }

    @CommandHandler
    public void handle(DelayTaskSubmitCmd cmd) {
        delayTaskScheduler.schedule(cmd.getTask());
    }


}
