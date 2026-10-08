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

package io.fluxion.server.infrastructure.dao.repository;

import io.fluxion.server.infrastructure.dao.entity.LockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

/**
 * @author Devil
 * @since 2022/7/18
 */
public interface LockEntityRepo extends JpaRepository<LockEntity, String> {

    LockEntity findByName(String name);

    /**
     * 解锁删除：按 name+owner 双匹配删除持有的锁行。
     * 刻意使用单语句 JPQL bulk delete（而非 Spring Data 衍生删除）：衍生删除走
     * SELECT 载入实体→remove→flush 逐主键 delete 的多阶段路径，事务窗口更长且经
     * JDBC batch 抛 BatchUpdateException，与并发的锁获取 ON-DUP 升级窗口更易碰撞；
     * bulk delete 单语句直发，把锁窗口压到最短。
     */
    @Modifying
    @Query("delete from LockEntity l where l.name = :name and l.owner = :owner")
    int deleteByNameAndOwner(@Param("name") String name, @Param("owner") String owner);

    /**
     * 原子性条件更新或插入锁记录（MySQL 方言）。
     * 
     * 成功条件：
     *   1. 记录不存在（新插入）
     *   2. 记录已过期（expire_at <= NOW(3)，覆盖）
     * 
     * 使用 MySQL INSERT ... ON DUPLICATE KEY UPDATE 实现原子性，避免 find-then-save 的竞争条件。
     * 只有在记录不存在或已过期时才会更新 owner，否则 update 操作不影响任何行。
     * 
     * @param name 锁名称
     * @param owner 期望的锁持有者（仅在插入或过期时生效）
     * @param expireAt 过期时间
     * @return 影响的行数：1 表示成功获取锁，0 表示获取失败（被其他持有者锁定且未过期）
     */
    @Modifying
    @Query(value = 
        "INSERT INTO fluxion_lock (name, owner, expire_at, is_deleted, created_at, updated_at) " +
        "VALUES (:name, :owner, :expireAt, 0, NOW(3), NOW(3)) " +
        "ON DUPLICATE KEY UPDATE " +
        "  owner = IF(expire_at <= NOW(3), :owner, owner), " +
        "  expire_at = IF(expire_at <= NOW(3), :expireAt, expire_at), " +
        "  updated_at = IF(expire_at <= NOW(3), NOW(3), updated_at), " +
        "  is_deleted = 0",
        nativeQuery = true)
    int tryAcquireLock(
        @Param("name") String name,
        @Param("owner") String owner,
        @Param("expireAt") Instant expireAt
    );

    /**
     * 查询锁记录的当前 owner。
     * 
     * @param name 锁名称
     * @return 当前 owner，如果不存在则返回 null
     */
    @Query("SELECT l.owner FROM LockEntity l WHERE l.name = :name AND (l.expireAt IS NULL OR l.expireAt > CURRENT_TIMESTAMP)")
    String findOwnerByName(@Param("name") String name);
}
