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

import java.time.LocalDateTime;

/**
 * @author Devil
 * @since 2022/7/18
 */
public interface LockEntityRepo extends JpaRepository<LockEntity, String> {

    LockEntity findByName(String name);

    int deleteByNameAndOwner(String name, String owner);

    /**
     * 原子性条件更新或插入锁记录。
     * 
     * 成功条件：
     *   1. 记录不存在（新插入）
     *   2. 记录已过期（expireAt <= now，覆盖）
     *   3. 记录属于同一 owner（owner 相同，重新加锁/刷新过期时间）
     * 
     * 使用 H2 的 MERGE INTO 实现原子性，避免 find-then-save 的竞争条件。
     * 
     * @param name 锁名称
     * @param owner 锁持有者
     * @param expireAt 过期时间
     * @param now 当前时间（用于比较过期）
     * @return 影响的行数：1 表示成功获取锁，0 表示获取失败（被其他持有者锁定且未过期）
     */
    @Modifying
    @Query(value = 
        "MERGE INTO fluxion_lock t " +
        "USING (SELECT 1) ON (t.name = :name) " +
        "WHEN NOT MATCHED THEN " +
        "  INSERT (name, owner, expire_at, is_deleted, created_at, updated_at) " +
        "  VALUES (:name, :owner, :expireAt, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
        "WHEN MATCHED AND (t.expire_at <= :now OR t.owner = :owner) THEN " +
        "  UPDATE SET t.owner = :owner, t.expire_at = :expireAt, t.updated_at = CURRENT_TIMESTAMP",
        nativeQuery = true)
    int tryAcquireLock(
        @Param("name") String name,
        @Param("owner") String owner,
        @Param("expireAt") LocalDateTime expireAt,
        @Param("now") LocalDateTime now
    );
}
