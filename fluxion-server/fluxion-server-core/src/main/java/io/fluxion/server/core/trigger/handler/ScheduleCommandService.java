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

package io.fluxion.server.core.trigger.handler;

import io.fluxion.server.core.broker.BrokerManger;
import io.fluxion.server.core.broker.BrokerNode;
import io.fluxion.server.core.trigger.cmd.ScheduleBrokerElectCmd;
import io.fluxion.server.core.trigger.cmd.ScheduleSaveCmd;
import io.fluxion.server.infrastructure.dao.entity.ScheduleEntity;
import io.fluxion.server.infrastructure.dao.repository.ScheduleEntityRepo;
import io.fluxion.server.infrastructure.lock.DistributedLock;
import io.fluxion.server.infrastructure.utils.JpaHelper;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author Devil
 */
@Service
public class ScheduleCommandService {

    @Resource
    private ScheduleEntityRepo scheduleEntityRepo;

    @Resource
    private BrokerManger brokerManger;

    @Resource
    private DistributedLock distributedLock;

    @CommandHandler
    public void handle(ScheduleSaveCmd cmd) {
        ScheduleEntity entity = ScheduleEntityConverter.convert(cmd.getSchedule());
        scheduleEntityRepo.saveAndFlush(entity);
        // todo @d 如果是首次创建，立即进行调度 否则保存下次触发时间为最近一次触发时间
    }

    @CommandHandler
    public void handle(ScheduleBrokerElectCmd cmd) {
        Pageable pageable = JpaHelper.pageable(1, 10);
        Page<ScheduleEntity> page = scheduleEntityRepo.findByBrokerIdAndDeletedContaining(cmd.getBrokerId(), false, pageable);
        while (!page.isEmpty()) {
            List<ScheduleEntity> entities = page.toList();
            for (ScheduleEntity entity : entities) {
                BrokerNode elect = brokerManger.elect(entity.getScheduleId());
                entity.setBrokerId(elect.id());
            }
            scheduleEntityRepo.saveAllAndFlush(entities);
            page = scheduleEntityRepo.findByBrokerIdAndDeletedContaining(cmd.getBrokerId(), false, pageable);
        }
    }

}
