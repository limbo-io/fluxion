/*
 * Copyright 2025-2030 Fluxion Team (https://github.com/Fluxion-io).
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

package io.fluxion.server.infrastructure.lock;

import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.infrastructure.dao.entity.LockEntity;
import io.fluxion.server.infrastructure.dao.repository.LockEntityRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.transaction.Transactional;
import java.time.temporal.ChronoUnit;

/**
 * @author Devil
 * @since 2024/1/14
 */
@Slf4j
@Component
public class DatabaseDistributedLock implements DistributedLock {

    @Resource
    private LockEntityRepo lockEntityRepo;

    @Override
    @Transactional
    public boolean tryLock(String name, long expire) {
        LockEntity lock = lockEntityRepo.findByName(name);

        // 防止同节点并发问题
        String current = owner();

        // 如果锁未过期且当前节点非加锁节点，加锁失败
        if (lock != null && lock.getExpireAt().isBefore(TimeUtils.currentLocalDateTime()) && !lock.getOwner().equals(current)) {
            return false;
        }
        if (lock == null) {
            lock = new LockEntity();
        }
        lock.setName(name);
        lock.setOwner(current);
        lock.setExpireAt(TimeUtils.currentLocalDateTime().plus(expire, ChronoUnit.MILLIS));
        return dbLock(lock);
    }

    @Override
    @Transactional
    public void lock(String name, long expire, long wait) {

    }

    private String owner() {
        return BrokerContext.broker().id() + "_" + Thread.currentThread().getId();
    }

    private boolean dbLock(LockEntity lock) {
        try {
            lockEntityRepo.saveAndFlush(lock);
            return true;
        } catch (DataIntegrityViolationException dive) {
            // 数据重复
            return false;
        } catch (Exception e) {
            log.warn("[DistributedLock] lock failed, name = {}.", lock.getName(), e);
            return false;
        }
    }

    @Override
    @Transactional
    public boolean unlock(String name) {
        return lockEntityRepo.deleteByNameAndOwner(name, owner()) > 0;
    }
}
