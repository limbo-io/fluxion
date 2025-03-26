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

import io.fluxion.server.infrastructure.dao.entity.WorkerExecutorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author Devil
 */
@Repository
public interface WorkerExecutorEntityRepo extends JpaRepository<WorkerExecutorEntity, WorkerExecutorEntity.ID> {

    @Modifying(clearAutomatically = true)
    @Query("delete from WorkerExecutorEntity where id.workerId = :workerId")
    int deleteByWorkerId(@Param("workerId") String workerId);

    List<WorkerExecutorEntity> findById_WorkerIdIn(List<String> workerIds);
}
