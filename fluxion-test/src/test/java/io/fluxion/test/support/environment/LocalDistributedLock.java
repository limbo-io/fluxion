/*
 * Copyright 2025-2030 Limbo Team (https://github.com/limbo-io).
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

package io.fluxion.test.support.environment;

import io.fluxion.server.infrastructure.lock.DistributedLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 【测试基础设施 - 本地分布式锁】
 * 
 * 作用：在单节点测试环境中模拟分布式锁行为
 *       用于替换真实的 DatabaseDistributedLock，避免依赖数据库锁表
 * 
 * 实现原理：
 *   - 使用 ConcurrentHashMap 存储锁状态（内存锁）
 *   - 支持阻塞等待获取锁（带超时）
 *   - 支持自动解锁（try-finally 保护）
 * 
 * ⚠️ 限制：
 *   - 仅限单 JVM 使用（单节点测试）
 *   - 无法实现真正的分布式锁语义（跨进程互斥）
 *   - 测试环境专用，生产环境请使用 DatabaseDistributedLock
 * 
 * 配置方式：
 *   在 TestApplication 中通过 @Bean @Primary 替换默认锁实现
 * 
 * @see io.fluxion.server.infrastructure.lock.DistributedLock
 * @see io.fluxion.test.support.environment.TestApplication
 */
public class LocalDistributedLock implements DistributedLock {
    
    private static final Logger log = LoggerFactory.getLogger(LocalDistributedLock.class);
    
    /**
     * 锁状态表：key=锁名称, value=是否被锁定
     */
    private final ConcurrentHashMap<String, Boolean> locks = new ConcurrentHashMap<>();

    /**
     * 获取锁并执行业务逻辑（阻塞模式）
     * 
     * 执行流程：
     *   1. 尝试获取锁（带等待超时）
     *   2. 执行业务逻辑（supplier）
     *   3. 自动释放锁（try-finally 保证）
     * 
     * @param name   锁名称（业务标识）
     * @param expire 锁过期时间（本地实现忽略此参数）
     * @param wait   最大等待时间（毫秒）
     * @param supplier 业务逻辑
     * @return 业务逻辑返回值
     * @throws RuntimeException 获取锁失败时抛出
     */
    @Override
    public <T> T lock(String name, long expire, long wait, Supplier<T> supplier) {
        long endTime = System.currentTimeMillis() + wait;
        boolean locked = false;
        
        // 循环尝试获取锁，直到超时
        try {
            do {
                if (tryLock(name, expire)) {
                    locked = true;
                    break;
                }
                // 等待 50ms 后重试
                Thread.sleep(50);
            } while (System.currentTimeMillis() < endTime);
        } catch (Exception e) {
            log.error("[LocalDistributedLock] 获取锁异常 name={}", name, e);
        }
        
        if (!locked) {
            throw new RuntimeException("[LocalDistributedLock] 获取锁失败: " + name);
        }
        
        // 执行业务逻辑，确保最终释放锁
        try {
            return supplier.get();
        } finally {
            unlock(name);
        }
    }

    /**
     * 尝试获取锁（非阻塞）
     * 
     * @param name   锁名称
     * @param expire 锁过期时间（本地实现忽略）
     * @return true=获取成功, false=已被占用
     */
    @Override
    public boolean tryLock(String name, long expire) {
        // putIfAbsent: 不存在时放入，返回null表示成功
        return locks.putIfAbsent(name, Boolean.TRUE) == null;
    }

    /**
     * 释放锁
     * 
     * @param name 锁名称
     * @return true=释放成功, false=锁不存在
     */
    @Override
    public boolean unlock(String name) {
        return locks.remove(name) != null;
    }
}
