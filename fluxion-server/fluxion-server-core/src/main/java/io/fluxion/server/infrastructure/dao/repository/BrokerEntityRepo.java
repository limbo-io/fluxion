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

import io.fluxion.server.infrastructure.dao.entity.BrokerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.transaction.Transactional;
import java.time.Instant;
import java.util.List;

/**
 * @author Devil
 * @since 2022/7/18
 */
public interface BrokerEntityRepo extends JpaRepository<BrokerEntity, BrokerEntity.ID> {

    List<BrokerEntity> findByLastHeartbeatAtBetween(Instant startTime, Instant endTime);

    /**
     * 心跳专用条件更新：只推进存活/负载字段，不触碰 created_at。
     * 通过 saveAndFlush(merge) 写心跳会把 detached 实体的 createdAt=null 覆盖进实体“更新集”，
     * 使 UPDATE 因 created_at NOT NULL 失败（见集群 E2E 实测）；心跳必须走本显式更新。
     *
     * @return 受影响行数；0 表示记录不存在，调用方回退插入路径
     */
    @Transactional
    @Modifying
    @Query("update BrokerEntity b set b.lastHeartbeatAt = :at, b.updatedAt = :at,"
        + " b.brokerLoad = :load, b.protocol = :protocol"
        + " where b.id = :id")
    int heartbeat(@Param("id") BrokerEntity.ID id,
                  @Param("at") Instant at,
                  @Param("load") int load,
                  @Param("protocol") String protocol);
}
