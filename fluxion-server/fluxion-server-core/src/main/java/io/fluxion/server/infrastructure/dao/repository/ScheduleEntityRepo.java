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

import io.fluxion.server.infrastructure.dao.entity.ScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * @author Devil
 */
@Repository
public interface ScheduleEntityRepo extends JpaRepository<ScheduleEntity, String> {

    @Modifying(clearAutomatically = true)
    @Query(value = "update ScheduleEntity set enabled = :enabled where scheduleTaskId = :scheduleTaskId")
    int updateEnable(@Param("scheduleTaskId") String scheduleTaskId, @Param("enabled") boolean enabled);

    @Query(value = "select e from ScheduleEntity e" +
        " where e.brokerId = :brokerId and e.updatedAt >= :updatedAt " +
        " and e.enabled = true and e.deleted = false ")
    List<ScheduleEntity> loadByBrokerAndUpdated(@Param("brokerId") String brokerId, @Param("updatedAt") LocalDateTime updatedAt);

    ScheduleEntity findByScheduleIdAndDeleted(@Param("scheduleId") String scheduleId, @Param("deleted") boolean deleted);
}
