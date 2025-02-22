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

package io.fluxion.server.infrastructure.id.handler;

import io.fluxion.common.utils.MD5Utils;
import io.fluxion.server.infrastructure.dao.entity.IdEntity;
import io.fluxion.server.infrastructure.dao.repository.IdEntityRepo;
import io.fluxion.server.infrastructure.id.cmd.IDGenerateCmd;
import io.fluxion.server.infrastructure.id.data.IDType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Devil
 */
@Slf4j
@Service
public class IDCommandService {

    private static final Map<IDType, ID> ID_MAP = new ConcurrentHashMap<>();

    @Resource
    private IdEntityRepo idEntityRepo;

    @CommandHandler
    public IDGenerateCmd.Response handle(IDGenerateCmd cmd) {
        Long autoIncrId = gainRandomAutoId(cmd.getType());
        String randomId = MD5Utils.md5(String.valueOf(autoIncrId));
        return new IDGenerateCmd.Response(randomId);
    }

    /**
     * Insert initial data into the database
     */
    @PostConstruct
    @Transactional
    public void registerIds() {
        for (IDType idType : IDType.values()) {
            String typeName = idType.name();
            IdEntity idEntity = idEntityRepo.findById(typeName).orElse(null);
            if (idEntity == null) {
                idEntity = new IdEntity();
                idEntity.setType(typeName);
                idEntity.setCurrentId(10000L);
                idEntity.setStep(100);
                idEntityRepo.saveAndFlush(idEntity);
            }
        }
    }

    private Long gainRandomAutoId(final IDType type) {
        synchronized (type) {
            ID id = ID_MAP.get(type);
            if (id == null || !id.valid()) {
                id = getNewId(type);
            }
            return id.getCurrentId().incrementAndGet();
        }
    }

    private ID getNewId(IDType type) {
        String typeName = type.name();

        int updateNum = 0; // 更新库存的条数
        int time = 0; // 重试次数

        long startId = 0;
        long endId = 0;

        while (updateNum <= 0 && time < 10) {
            try {
                // 加锁 获取 类型
                IdEntity idEntity = idEntityRepo.findById(typeName).orElse(null);
                startId = idEntity.getCurrentId();
                endId = idEntity.getCurrentId() + idEntity.getStep();
                updateNum = idEntityRepo.casGainId(typeName, endId, startId);
                Thread.sleep(200);
            } catch (InterruptedException e) {
                log.error("thread sleep fail", e);
                Thread.currentThread().interrupt();
            }
            time++;
        }

        if (updateNum <= 0) {
            throw new IllegalStateException("The system is busy, Try again later!!!");
        }

        ID id = new ID(new AtomicLong(startId), endId);
        ID_MAP.put(type, id);
        return id;
    }

    @Getter
    @AllArgsConstructor
    private static class ID {
        private AtomicLong currentId;

        private Long endId;

        public boolean valid() {
            return currentId.get() < endId;
        }
    }
}
