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

import io.fluxion.server.infrastructure.dao.entity.ExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author Devil
 */
@Repository
public interface ExecutionEntityRepo extends JpaRepository<ExecutionEntity, String>, JpaSpecificationExecutor<ExecutionEntity> {

    ExecutionEntity findByExecutableIdAndExecutableTypeAndTriggerAt(String executableId, String executableType, LocalDateTime triggerAt);

    /**
     * Find executions by status list and bucket list
     * Note: executions may not have bucket field directly, this is for potential bucket-based queries
     */
    List<ExecutionEntity> findByStatusIn(List<String> statuses);

    /**
     * Find active executions with expired leases for recovery
     * Active statuses: RUNNING, RESTARTED (considered as actively running)
     */
    @Query("SELECT e FROM ExecutionEntity e WHERE e.status IN :statuses AND (e.leaseUntil IS NULL OR e.leaseUntil < :now)")
    List<ExecutionEntity> findActiveExecutionsWithExpiredLeases(
            @Param("statuses") List<String> statuses,
            @Param("now") LocalDateTime now
    );

    /**
     * Find executions by lease owner (broker ID)
     */
    List<ExecutionEntity> findByLeaseOwner(String leaseOwner);

    /**
     * Find executions by worker ID or lease owner
     */
    List<ExecutionEntity> findByWorkerIdOrLeaseOwner(String workerId, String leaseOwner);
}
