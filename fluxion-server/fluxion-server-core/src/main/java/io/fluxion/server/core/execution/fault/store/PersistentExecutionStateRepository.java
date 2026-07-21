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

package io.fluxion.server.core.execution.fault.store;

import io.fluxion.server.core.execution.fault.ExecutionState;
import io.fluxion.server.core.execution.fault.ExecutionInfo;
import io.fluxion.server.infrastructure.dao.entity.ExecutionEntity;
import io.fluxion.server.infrastructure.dao.repository.ExecutionEntityRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Persistent execution state repository - stores execution fault tolerance state in database
 */
@Component
@Slf4j
public class PersistentExecutionStateRepository {

    @Resource
    private ExecutionEntityRepo executionEntityRepo;

    /**
     * Register execution state to persistent storage
     */
    @Transactional
    public void register(ExecutionInfo execution) {
        log.debug("[PERSISTENT-STATE] Registering execution: executionId={}", execution.getExecutionId());
        
        ExecutionEntity entity = executionEntityRepo.findById(execution.getExecutionId()).orElse(null);
        if (entity == null) {
            log.warn("[PERSISTENT-STATE] Execution not found in DB: executionId={}", execution.getExecutionId());
            return;
        }

        // Update fault tolerance state fields
        entity.setWorkerId(execution.getWorkerId());
        entity.setStateUpdatedAt(LocalDateTime.now());
        
        executionEntityRepo.save(entity);
        log.debug("[PERSISTENT-STATE] Execution registered: executionId={}", execution.getExecutionId());
    }

    /**
     * Update execution state and lease information
     */
    @Transactional
    public void updateState(String executionId, ExecutionState state, String workerId, String leaseOwner, LocalDateTime leaseUntil) {
        log.debug("[PERSISTENT-STATE] Updating state: executionId={}, state={}", executionId, state);
        
        ExecutionEntity entity = executionEntityRepo.findById(executionId).orElse(null);
        if (entity == null) {
            log.warn("[PERSISTENT-STATE] Cannot update state for unknown execution: executionId={}", executionId);
            return;
        }

        entity.setStatus(mapStateToStatus(state));
        entity.setWorkerId(workerId);
        entity.setLeaseOwner(leaseOwner);
        entity.setLeaseUntil(leaseUntil);
        entity.setStateUpdatedAt(LocalDateTime.now());
        
        executionEntityRepo.save(entity);
        log.debug("[PERSISTENT-STATE] State updated: executionId={}, state={}", executionId, state);
    }

    /**
     * Get active executions for recovery (those with expired or no leases)
     */
    public List<ExecutionInfo> getActiveExecutionsForRecovery(List<Integer> buckets) {
        log.debug("[PERSISTENT-STATE] Querying active executions for recovery");
        
        // Active statuses for fault tolerance purposes
        List<String> activeStatuses = List.of("running", "restarted");
        
        if (buckets.isEmpty()) {
            return List.of();
        }
        List<ExecutionEntity> entities = executionEntityRepo.findActiveExecutionsForRecovery(activeStatuses, buckets);
        
        return entities.stream()
                .map(this::toExecutionInfo)
                .collect(Collectors.toList());
    }

    @Transactional
    public boolean tryClaimRecoveryLease(String executionId, String brokerId, int leaseSeconds) {
        return executionEntityRepo.claimRecoveryLease(executionId, brokerId, leaseSeconds) == 1;
    }

    /**
     * Query executions by worker ID
     */
    public List<ExecutionInfo> queryByWorker(String workerId) {
        log.debug("[PERSISTENT-STATE] Querying by worker: workerId={}", workerId);
        
        // Query by worker_id or lease_owner
        List<ExecutionEntity> entities = executionEntityRepo.findByWorkerIdOrLeaseOwner(workerId, workerId);
        
        return entities.stream()
                .map(this::toExecutionInfo)
                .collect(Collectors.toList());
    }

    /**
     * Remove execution from persistent state tracking
     * This updates the state to terminal and clears lease
     */
    @Transactional
    public void remove(String executionId) {
        log.debug("[PERSISTENT-STATE] Removing execution: executionId={}", executionId);
        
        ExecutionEntity entity = executionEntityRepo.findById(executionId).orElse(null);
        if (entity == null) {
            return;
        }

        // Clear fault tolerance tracking fields
        entity.setLeaseOwner(null);
        entity.setLeaseUntil(null);
        entity.setStateUpdatedAt(LocalDateTime.now());
        
        executionEntityRepo.save(entity);
        log.debug("[PERSISTENT-STATE] Execution removed: executionId={}", executionId);
    }

    /**
     * Increment dispatch attempt counter
     */
    @Transactional
    public void incrementDispatchAttempt(String executionId) {
        ExecutionEntity entity = executionEntityRepo.findById(executionId).orElse(null);
        if (entity == null) {
            return;
        }

        Integer currentAttempt = entity.getDispatchAttempt();
        if (currentAttempt == null) {
            currentAttempt = 0;
        }
        entity.setDispatchAttempt(currentAttempt + 1);
        entity.setStateUpdatedAt(LocalDateTime.now());
        
        executionEntityRepo.save(entity);
    }

    /**
     * Convert ExecutionEntity to ExecutionInfo
     */
    private ExecutionInfo toExecutionInfo(ExecutionEntity entity) {
        ExecutionState state = mapStatusToState(entity.getStatus());
        
        return ExecutionInfo.builder()
                .executionId(entity.getExecutionId())
                .workerId(entity.getWorkerId())
                .state(state)
                .startTime(entity.getStartAt() != null ? entity.getStartAt().toInstant(java.time.ZoneOffset.UTC).toEpochMilli() : 0)
                .build();
    }

    /**
     * Map ExecutionState to database status string
     */
    private String mapStateToStatus(ExecutionState state) {
        return switch (state) {
            case PENDING, DISPATCHED, RUNNING -> "running";
            case SUCCEEDED -> "succeed";
            case FAILED, TIMEOUT -> "failed";
            case RETRYING -> "restarted";
            case MIGRATING -> "running";  // Keep as running while migrating
            case CANCELLED -> "cancelled";
        };
    }

    /**
     * Map database status string to ExecutionState
     */
    private ExecutionState mapStatusToState(String status) {
        if (status == null) {
            return ExecutionState.PENDING;
        }
        return switch (status) {
            case "running" -> ExecutionState.RUNNING;
            case "succeed" -> ExecutionState.SUCCEEDED;
            case "failed" -> ExecutionState.FAILED;
            case "restarted" -> ExecutionState.RETRYING;
            case "cancelled" -> ExecutionState.CANCELLED;
            default -> ExecutionState.PENDING;
        };
    }
}
