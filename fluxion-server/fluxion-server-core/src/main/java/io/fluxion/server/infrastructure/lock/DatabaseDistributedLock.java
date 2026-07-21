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

import io.limbo.utils.time.TimeUtils;
import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.infrastructure.dao.repository.LockEntityRepo;
import io.fluxion.server.infrastructure.dao.tx.TransactionService;
import io.fluxion.server.infrastructure.exception.ErrorCode;
import io.fluxion.server.infrastructure.exception.PlatformException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 数据库分布式锁实现。
 * 
 * 关键改进（修复并发问题和所有权bug）：
 * 1. 使用 UUID 令牌作为锁持有者标识，避免 brokerId + threadId 的冲突风险
 * 2. 原子性条件更新（INSERT...ON DUPLICATE KEY UPDATE 语义）
 *    - 更新仅当：记录不存在、已过期（expire_at <= now）、或同一持有者
 * 3. 修复过期逻辑：原代码使用 isBefore 导致逻辑反转，现在正确判断 lock NOT expired AND different owner
 * 4. 解锁时传递令牌，防止持有者 A 解锁持有者 B 的锁
 * 
 * @author Devil
 * @since 2024/1/14
 */
@Slf4j
@Component
public class DatabaseDistributedLock implements DistributedLock {

    @Resource
    private LockEntityRepo lockEntityRepo;

    @Resource
    private TransactionService transactionService;

    /**
     * 锁持有令牌映射：key=锁名称, value=当前线程持有的令牌
     * 用于在同一线程内重入和解锁时验证所有权
     */
    private final ConcurrentHashMap<String, String> lockTokens = new ConcurrentHashMap<>();

    @Override
    public <T> T lock(String name, long expire, long wait, Supplier<T> supplier) {
        long endTime = System.currentTimeMillis() + wait;
        String token = null;
        try {
            do {
                token = tryLockInternal(name, expire);
                if (token != null) {
                    lockTokens.put(name, token);
                    break;
                }
                Thread.sleep(50);
            } while (System.currentTimeMillis() < endTime);
        } catch (Exception e) {
            log.error("[DistributedLock] lock error name:{}", name, e);
        }
        if (!lockTokens.containsKey(name)) {
            throw new PlatformException(ErrorCode.SYSTEM_ERROR, "[DistributedLock] get lock " + name + " failed");
        }
        try {
            return supplier.get();
        } finally {
            unlockInternal(name, lockTokens.remove(name));
        }
    }

    @Override
    public boolean tryLock(String name, long expire) {
        String token = tryLockInternal(name, expire);
        if (token != null) {
            lockTokens.put(name, token);
            return true;
        }
        return false;
    }

    /**
     * 尝试获取锁，返回令牌如果成功，null 如果失败。
     * 使用原子性条件更新确保并发安全。
     */
    private String tryLockInternal(String name, long expire) {
        // 生成唯一令牌：brokerId + UUID（避免 threadId 冲突）
        String token = generateToken();
        LocalDateTime expireAt = TimeUtils.currentLocalDateTime().plus(expire, ChronoUnit.MILLIS);
        LocalDateTime now = TimeUtils.currentLocalDateTime();

        // 原子性尝试获取锁：
        // - 成功返回 1：记录不存在、已过期、或同持有者
        // - 失败返回 0：被其他持有者锁定且未过期
        return transactionService.transactional(() -> {
            int affected = lockEntityRepo.tryAcquireLock(name, token, expireAt, now);
            if (affected > 0) {
                if (log.isDebugEnabled()) {
                    log.debug("[DistributedLock] acquired lock: name={}, owner={}, expireAt={}", 
                        name, token, expireAt);
                }
                return token;
            }
            return null;
        });
    }

    @Override
    public boolean unlock(String name) {
        String token = lockTokens.remove(name);
        if (token == null) {
            // 如果没有记录令牌（可能是异常路径），尝试查找并删除当前 owner
            // 但这里更安全的方式是不删除，避免误删他人锁
            log.warn("[DistributedLock] unlock called without holding lock: name={}", name);
            return false;
        }
        return unlockInternal(name, token);
    }

    /**
     * 使用令牌解锁，确保只能删除自己持有的锁。
     */
    private boolean unlockInternal(String name, String token) {
        if (token == null) {
            return false;
        }
        return transactionService.transactional(() -> {
            int deleted = lockEntityRepo.deleteByNameAndOwner(name, token);
            if (deleted > 0 && log.isDebugEnabled()) {
                log.debug("[DistributedLock] released lock: name={}, owner={}", name, token);
            }
            return deleted > 0;
        });
    }

    /**
     * 生成唯一令牌：brokerId + UUID。
     * 比之前使用的 brokerId + threadId 更安全，避免：
     * 1. 不同 broker 的 threadId 冲突
     * 2. 线程复用导致的持有者混淆
     */
    private String generateToken() {
        String brokerId = BrokerContext.broker() != null ? BrokerContext.broker().id() : "unknown";
        return brokerId + "_" + UUID.randomUUID().toString();
    }
}
