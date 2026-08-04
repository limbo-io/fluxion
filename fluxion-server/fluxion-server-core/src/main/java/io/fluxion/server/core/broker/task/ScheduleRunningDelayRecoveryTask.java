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
import io.fluxion.server.core.broker.query.BucketsByBrokerQuery;
import io.fluxion.server.core.schedule.ScheduleLeaseProperties;
import io.fluxion.server.core.schedule.cmd.ScheduleRunningDelayRecoverCmd;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import io.limbo.cqrs.spring.command.Cmd;
import io.limbo.cqrs.spring.query.Query;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Scans the current broker's buckets for RUNNING delays left by a crashed broker.
 *
 * @author Devil
 */
public class ScheduleRunningDelayRecoveryTask extends CoreTask {

    public ScheduleRunningDelayRecoveryTask(ScheduleLeaseProperties properties) {
        super(0, properties.getReclaimInterval(), TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        if (BrokerContext.broker() == null) {
            return;
        }
        List<Integer> buckets = Query.query(new BucketsByBrokerQuery(BrokerContext.broker().id())).getBuckets();
        if (!buckets.isEmpty()) {
            Cmd.send(new ScheduleRunningDelayRecoverCmd(buckets));
        }
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.FIXED_RATE;
    }
}
