/*
 * Copyright 2025-2030 limbo-io Team (https://github.com/limbo-io).
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

package io.fluxion.server.core.broker.service;

import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.broker.BrokerManger;
import io.fluxion.server.core.broker.BrokerNode;
import io.fluxion.server.core.broker.cmd.BucketAllotCmd;
import io.fluxion.server.core.broker.cmd.BucketRebalanceCmd;
import io.fluxion.server.infrastructure.dao.entity.BucketEntity;
import io.fluxion.server.infrastructure.dao.repository.BucketEntityRepo;
import io.fluxion.server.infrastructure.lock.DistributedLock;
import org.apache.commons.collections4.CollectionUtils;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据分区 bucket
 *
 * @author Devil
 */
@Service
public class BucketCommandService {

    private static final int BUCKET_SIZE = 64;

    private static final String REBALANCE_LOCK = "BUCKET_REBALANCE";

    @Resource
    private BucketEntityRepo bucketEntityRepo;

    @Resource
    private DistributedLock distributedLock;

    @Resource
    private BrokerManger brokerManger;

    @CommandHandler
    public BucketAllotCmd.Response handle(BucketAllotCmd cmd) {
        // hash获取id对应的值
        int bucket = Math.abs(cmd.getResourceId().hashCode()) % BUCKET_SIZE + 1;
        return new BucketAllotCmd.Response(bucket);
    }

    @CommandHandler
    public void handle(BucketRebalanceCmd cmd) {
        boolean locked = distributedLock.tryLock(REBALANCE_LOCK, 10000);
        if (!locked) {
            return;
        }
        try {
            String brokerId = BrokerContext.broker().id();
            List<String> brokerIds = brokerManger.allAlive().stream().map(BrokerNode::id).sorted().collect(Collectors.toList());
            List<BucketEntity> dbEntities = bucketEntityRepo.findAll();
            List<BucketEntity> entities = new ArrayList<>();
            // 判断是否需要重新选举
            for (BucketEntity entity : dbEntities) {
                if (brokerIds.contains(entity.getBrokerId())) {
                    continue;
                }
                // 为bucket分配新的broker
                int idx = entity.getBucket() % brokerIds.size();
                entity.setBrokerId(brokerIds.get(idx));
                entities.add(entity);
            }

            // 判断是否需要新增
            Set<Integer> buckets = dbEntities.stream().map(BucketEntity::getBucket).collect(Collectors.toSet());
            for (int i = 1; i <= BUCKET_SIZE; i++) {
                if (buckets.contains(i)) {
                    continue;
                }
                BucketEntity entity = new BucketEntity();
                entity.setBucket(i);
                entity.setBrokerId(brokerId);
                entities.add(entity);
            }
            if (CollectionUtils.isNotEmpty(entities)) {
                bucketEntityRepo.saveAllAndFlush(entities);
            }
        } finally {
            distributedLock.unlock(REBALANCE_LOCK);
        }
    }

}
