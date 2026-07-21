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
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 数据库分布式锁实现（MySQL 安全版本）。
 * 
 * 关键改进（修复并发问题和所有权bug）：
 * 1. 使用 ThreadLocal<String> 存储令牌，避免 static map 被失败线程污染
 * 2. 使用 UUID 令牌作为锁持有者标识，避免 JVM 全局冲突
 * 3. MySQL INSERT ... ON DUPLICATE KEY UPDATE 原子性条件更新
 *    - 更新仅当：记录不存在、已过期（expire_at <= NOW(3)）
 * 4. 解锁时要求传递 Locked 句柄，验证 name + owner 双重匹配
 * 5. 修复 wait 语义：wait < 0 无限重试，wait >= 0 带抖动退避直到截止
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
     * 线程本地锁令牌：每个线程拥有独立的锁持有记录。
     * ThreadLocal 确保：
     * 1. 失败的线程不会污染成功线程的令牌
     * 2. 线程之间天然隔离，无需同步
     * 3. 线程退出时自动清理，避免内存泄漏
     * 
     * key=锁名称, value=当前线程持有的令牌
     */
    private final ThreadLocal<java.util.Map<String, String>> threadLocalTokens = ThreadLocal.withInitial(java.util.HashMap::new);

    @Override
    public <T> T lock(String name, long expire, long wait, Supplier<T> supplier) {
        if (expire <= 0) {
            throw new PlatformException(ErrorCode.ILLEGAL_ARGUMENT, "expire must be positive");
        }
        
        Locked locked = doTryLockWithWait(name, expire, wait);
        if (locked == null) {
            throw new PlatformException(ErrorCode.SYSTEM_ERROR, "[DistributedLock] get lock " + name + " failed");
        }
        
        // 只有成功获取锁的线程才能执行 supplier
        try {
            return supplier.get();
        } finally {
            unlock(locked);
        }
    }

    @Override
    public boolean tryLock(String name, long expire) {
        if (expire <= 0) {
            return false;
        }
        
        Locked locked = tryAcquireLock(name, expire);
        if (locked != null) {
            // 存储令牌到 ThreadLocal
            threadLocalTokens.get().put(name, locked.token());
            return true;
        }
        return false;
    }

    /**
     * 尝试获取锁，返回 Locked 句柄（包含 name 和 token）。
     * 使用 MySQL INSERT ... ON DUPLICATE KEY UPDATE 实现原子性条件获取。
     */
    public Locked tryAcquireLock(String name, long expire) {
        String token = generateToken();
        LocalDateTime expireAt = TimeUtils.currentLocalDateTime().plus(expire, ChronoUnit.MILLIS);

        return transactionService.transactional(() -> {
            int affected = lockEntityRepo.tryAcquireLock(name, token, expireAt);
            if (affected > 0) {
                // 原子操作成功：我们取得了锁（无论是新插入还是覆盖了过期记录）
                if (log.isDebugEnabled()) {
                    log.debug("[DistributedLock] acquired lock: name={}, owner={}, expireAt={}", 
                        name, token, expireAt);
                }
                // 存储令牌到 ThreadLocal
                threadLocalTokens.get().put(name, token);
                return new Locked(name, token);
            }
            return null;
        });
    }

    /**
     * 带等待逻辑的锁获取。
     * 
     * @param name 锁名称
     * @param expire 锁过期时间（毫秒）
     * @param wait 等待时间（毫秒）：<0 表示无限重试，>=0 表示最多等待指定时间
     * @return Locked 句柄，如果获取失败返回 null
     */
    private Locked doTryLockWithWait(String name, long expire, long wait) {
        final long startTime = System.currentTimeMillis();
        final long deadline = wait >= 0 ? startTime + wait : Long.MAX_VALUE;
        
        // 初始退避间隔：50-150ms 随机
        long backoffMs = 50 + ThreadLocalRandom.current().nextInt(100);
        
        while (true) {
            Locked locked = tryAcquireLock(name, expire);
            if (locked != null) {
                return locked;
            }
            
            // 检查是否超过等待时间
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return null; // 超时，获取失败
            }
            
            // 计算本次休眠时间：取退避间隔和剩余时间的较小值
            long sleepMs = Math.min(backoffMs, remaining);
            if (sleepMs <= 0) {
                return null;
            }
            
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[DistributedLock] interrupted while waiting for lock: name={}", name);
                return null;
            }
            
            // 指数退避 + 抖动：上限 2秒
            backoffMs = Math.min(backoffMs * 2, 2000);
            backoffMs = backoffMs / 2 + ThreadLocalRandom.current().nextInt((int) backoffMs);
        }
    }

    /**
     * 使用 Locked 句柄释放锁（推荐方式）。
     * 验证 name + token 双重匹配，防止误释放其他线程的锁。
     */
    public boolean unlock(Locked locked) {
        if (locked == null || locked.token() == null) {
            return false;
        }
        
        // 验证 ThreadLocal 中存储的令牌匹配
        String storedToken = threadLocalTokens.get().get(locked.name());
        if (storedToken == null || !storedToken.equals(locked.token())) {
            log.warn("[DistributedLock] unlock token mismatch: name={}, expected in thread={}", 
                locked.name(), storedToken);
            return false;
        }
        
        threadLocalTokens.get().remove(locked.name());
        return unlockWithToken(locked.name(), locked.token());
    }

    @Override
    public boolean unlock(String name) {
        String token = threadLocalTokens.get().remove(name);
        if (token == null) {
            // 如果没有记录令牌（可能是异常路径或未曾持有），拒绝解锁
            log.warn("[DistributedLock] unlock called without holding lock: name={}", name);
            return false;
        }
        return unlockWithToken(name, token);
    }

    /**
     * 使用令牌解锁，确保只能删除自己持有的锁。
     * 使用 WHERE name=? AND owner=? 确保原子性验证。
     */
    private boolean unlockWithToken(String name, String token) {
        if (name == null || token == null) {
            return false;
        }
        
        return transactionService.transactional(() -> {
            int deleted = lockEntityRepo.deleteByNameAndOwner(name, token);
            if (deleted > 0) {
                if (log.isDebugEnabled()) {
                    log.debug("[DistributedLock] released lock: name={}, owner={}", name, token);
                }
                return true;
            } else {
                // 删除失败：可能锁已过期被他人获取，或从未持有
                log.warn("[DistributedLock] unlock failed - not owner or lock expired: name={}", name);
                return false;
            }
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
