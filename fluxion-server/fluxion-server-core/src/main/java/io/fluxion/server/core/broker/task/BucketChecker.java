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
import io.fluxion.server.core.broker.cmd.BucketRebalanceCmd;
import io.fluxion.server.core.broker.query.BucketsByBrokerQuery;
import io.fluxion.server.core.schedule.cmd.CancelTasksByBucketCmd;
import io.limbo.cqrs.spring.command.Cmd;
import io.limbo.cqrs.spring.query.Query;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * bucket对应的broker无效的，重新进行数据绑定
 * 同时处理bucket所有权变更：取消不再拥有的bucket中的任务
 *
 * @author Devil
 */
@Slf4j
public class BucketChecker extends CoreTask {

    private static final int INTERVAL = 30;
    private static final TimeUnit UNIT = TimeUnit.DAYS;

    /**
     * Tracks buckets owned by this broker in the previous check.
     * Used to detect bucket ownership changes.
     */
    private volatile Set<Integer> previousBuckets = new HashSet<>();

    public BucketChecker() {
        super(0, INTERVAL, UNIT);
    }

    @Override
    public void run() {
        // Get current buckets before rebalancing
        Set<Integer> currentBuckets = new HashSet<>(getCurrentBrokerBuckets());
        
        // Trigger rebalancing
        Cmd.send(new BucketRebalanceCmd());
        
        // After rebalancing, get new bucket assignment
        Set<Integer> newBuckets = new HashSet<>(getCurrentBrokerBuckets());
        
        // Find buckets that were lost
        Set<Integer> lostBuckets = new HashSet<>(currentBuckets);
        lostBuckets.removeAll(newBuckets);
        
        // Find buckets that were newly acquired
        Set<Integer> gainedBuckets = new HashSet<>(newBuckets);
        gainedBuckets.removeAll(currentBuckets);
        
        // Cancel in-memory tasks for lost buckets
        if (!lostBuckets.isEmpty()) {
            handleLostBuckets(new ArrayList<>(lostBuckets));
        }
        
        // Log bucket changes
        if (!lostBuckets.isEmpty() || !gainedBuckets.isEmpty()) {
            log.info("Bucket ownership changed - lost: {}, gained: {}, current: {}", 
                lostBuckets, gainedBuckets, newBuckets);
        }
        
        // Update previous buckets for next iteration
        previousBuckets = newBuckets;
    }
    
    /**
     * Handle buckets that are no longer owned by this broker.
     * Cancels in-memory scheduled tasks for these buckets.
     */
    private void handleLostBuckets(List<Integer> lostBuckets) {
        log.info("Cancelling in-memory tasks for lost buckets: {}", lostBuckets);
        try {
            Cmd.send(new CancelTasksByBucketCmd(lostBuckets));
        } catch (Exception e) {
            log.error("Failed to cancel tasks for lost buckets: {}", lostBuckets, e);
        }
    }
    
    private List<Integer> getCurrentBrokerBuckets() {
        String brokerId = getCurrentBrokerId();
        if (brokerId == null) {
            return new ArrayList<>();
        }
        return Query.query(new BucketsByBrokerQuery(brokerId)).getBuckets();
    }
    
    private String getCurrentBrokerId() {
        if (BrokerContext.broker() == null) {
            return null;
        }
        return BrokerContext.broker().id();
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.FIXED_DELAY;
    }
}
