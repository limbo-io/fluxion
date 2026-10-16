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

import io.fluxion.server.infrastructure.dao.entity.WorkerMetricEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * @author Devil
 */
@Repository
public interface WorkerMetricEntityRepo extends JpaRepository<WorkerMetricEntity, String> {
    List<WorkerMetricEntity> findByWorkerIdIn(Collection<String> workerIds);

    List<WorkerMetricEntity> findByLastHeartbeatAtBetween(Instant lastHeartbeatAtAfter, Instant lastHeartbeatAtBefore, Pageable pageable);

    /**
     * 心跳专用条件更新：仅刷新负载/心跳/updated_at，不触碰 created_at。
     * 与 Broker 心跳同因（见 BrokerEntityRepo.heartbeat）：saveAndFlush(merge)
     * 会把分配主键 detached 实体的 createdAt=null 覆盖进更新集，使 UPDATE 因 NOT NULL 失败。
     *
     * @return 受影响行数；0 表示记录不存在，调用方回退插入路径
     */
    @Transactional
    @Modifying
    @Query("update WorkerMetricEntity m"
        + " set m.cpuProcessors = :cpuProcessors, m.cpuLoad = :cpuLoad, m.freeMemory = :freeMemory,"
        + " m.availableQueueNum = :availableQueueNum, m.lastHeartbeatAt = :lastHeartbeatAt,"
        + " m.updatedAt = :updatedAt"
        + " where m.workerId = :workerId")
    int heartbeat(@Param("workerId") String workerId,
                  @Param("cpuProcessors") int cpuProcessors,
                  @Param("cpuLoad") double cpuLoad,
                  @Param("freeMemory") long freeMemory,
                  @Param("availableQueueNum") int availableQueueNum,
                  @Param("lastHeartbeatAt") Instant lastHeartbeatAt,
                  @Param("updatedAt") Instant updatedAt);
}
