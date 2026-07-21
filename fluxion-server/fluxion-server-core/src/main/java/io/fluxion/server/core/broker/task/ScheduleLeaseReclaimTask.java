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
import io.fluxion.server.core.schedule.cmd.ScheduleLeaseReclaimCmd;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import io.limbo.cqrs.spring.command.Cmd;
import io.limbo.cqrs.spring.query.Query;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Core task that periodically scans for and reclaims expired schedule delay leases.
 * Resets expired CLAIMED delays back to INIT status for re-claiming by any broker.
 *
 * @author Devil
 */
@Slf4j
public class ScheduleLeaseReclaimTask extends CoreTask {

    private final ScheduleLeaseProperties properties;

    public ScheduleLeaseReclaimTask(ScheduleLeaseProperties properties) {
        super(0, properties.getReclaimInterval(), TimeUnit.SECONDS);
        this.properties = properties;
    }

    @Override
    public void run() {
        if (BrokerContext.broker() == null) {
            return;
        }

        List<Integer> buckets = getCurrentBrokerBuckets();
        if (buckets.isEmpty()) {
            return;
        }

        try {
            Cmd.send(new ScheduleLeaseReclaimCmd(buckets));
            if (log.isDebugEnabled()) {
                log.debug("Sent lease reclaim command for buckets {}", buckets);
            }
        } catch (Exception e) {
            log.error("Failed to send lease reclaim command for buckets {}", buckets, e);
        }
    }

    private List<Integer> getCurrentBrokerBuckets() {
        String brokerId = BrokerContext.broker().id();
        return Query.query(new BucketsByBrokerQuery(brokerId)).getBuckets();
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.FIXED_RATE;
    }
}
