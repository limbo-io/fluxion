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

import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.schedule.ScheduleLeaseProperties;
import io.fluxion.server.core.schedule.cmd.ScheduleLeaseRenewCmd;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import io.limbo.cqrs.spring.command.Cmd;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * Core task that periodically renews lease ownership for schedule delays
 * claimed by the current broker. Runs at fixed rate to prevent lease expiration.
 *
 * @author Devil
 */
@Slf4j
public class ScheduleLeaseRenewTask extends CoreTask {

    private final ScheduleLeaseProperties properties;

    public ScheduleLeaseRenewTask(ScheduleLeaseProperties properties) {
        super(0, properties.getRenewInterval(), TimeUnit.SECONDS);
        this.properties = properties;
    }

    @Override
    public void run() {
        if (BrokerContext.broker() == null) {
            return;
        }
        String brokerId = BrokerContext.broker().id();
        try {
            Cmd.send(new ScheduleLeaseRenewCmd(brokerId, properties.getDuration()));
            if (log.isDebugEnabled()) {
                log.debug("Sent lease renew command for broker {}", brokerId);
            }
        } catch (Exception e) {
            log.error("Failed to send lease renew command for broker {}", brokerId, e);
        }
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.FIXED_RATE;
    }
}
