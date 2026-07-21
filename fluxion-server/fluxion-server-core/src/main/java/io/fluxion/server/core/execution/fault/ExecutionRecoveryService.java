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

package io.fluxion.server.core.execution.fault;

import io.fluxion.server.core.execution.fault.store.ExecutionStateStore;
import io.fluxion.server.core.execution.fault.store.PersistentExecutionStateRepository;
import io.fluxion.server.core.execution.fault.timeout.TimeoutManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Execution recovery service - recovers active executions on broker startup
 */
@Slf4j
@Service
public class ExecutionRecoveryService {

    @Resource
    private PersistentExecutionStateRepository persistentRepository;

    @Resource
    private ExecutionStateStore executionStateStore;

    @Resource
    private TimeoutManager timeoutManager;

    private final Duration defaultTimeout = Duration.ofMinutes(30);

    /**
     * Recover active executions for the given broker and buckets.
     * Called on broker startup to reclaim executions that were previously owned.
     *
     * @param brokerId The broker ID attempting recovery
     * @param buckets The bucket IDs this broker owns
     */
    @Transactional
    public void recoverExecutions(String brokerId, List<Integer> buckets) {
        log.info("[RECOVERY] Starting execution recovery for broker={} on buckets={}", brokerId, buckets);

        // 1. Query active executions with expired leases from DB
        List<ExecutionInfo> activeExecutions = persistentRepository.getActiveExecutionsForRecovery();

        if (activeExecutions.isEmpty()) {
            log.info("[RECOVERY] No active executions to recover for broker={}", brokerId);
            return;
        }

        log.info("[RECOVERY] Found {} active executions to process", activeExecutions.size());

        int recoveredCount = 0;
        int timeoutCount = 0;
        int claimFailedCount = 0;

        for (ExecutionInfo execution : activeExecutions) {
            String executionId = execution.getExecutionId();

            // 2. Try to claim lease (conditional update)
            boolean claimed = tryClaimLease(executionId, brokerId);

            if (!claimed) {
                log.warn("[RECOVERY] Claim failed for execution: executionId={}", executionId);
                claimFailedCount++;
                continue;
            }

            // 3. Check if already timed out
            long now = System.currentTimeMillis();
            long timeoutTimestamp = execution.getTimeoutTimestamp();

            if (timeoutTimestamp > 0 && now > timeoutTimestamp) {
                // Already timed out - let handleTimeout process it
                log.warn("[RECOVERY] Execution already timed out, skipping: executionId={}", executionId);
                timeoutCount++;
                continue;
            }

            // Update execution state for recovery
            execution.setState(ExecutionState.RUNNING);
            if (execution.getStartTime() == 0) {
                execution.setStartTime(now);
            }

            // Calculate remaining timeout
            long remainingTimeout = defaultTimeout.toMillis();
            if (timeoutTimestamp > 0) {
                remainingTimeout = Math.max(0, timeoutTimestamp - now);
            } else {
                // Set timeout timestamp if not set
                timeoutTimestamp = now + defaultTimeout.toMillis();
                execution.setTimeoutTimestamp(timeoutTimestamp);
            }

            // 4. Register to ExecutionStateStore (memory index)
            executionStateStore.add(execution);

            // 4. Add to TimeoutManager
            timeoutManager.addTimeout(executionId, Duration.ofMillis(remainingTimeout),
                    this::onExecutionTimeout);

            recoveredCount++;
            log.info("[RECOVERY] Successfully recovered execution: executionId={}, workerId={}",
                    executionId, execution.getWorkerId());
        }

        log.info("[RECOVERY] Execution recovery complete for broker={}: recovered={}, timedOut={}, claimFailed={}",
                brokerId, recoveredCount, timeoutCount, claimFailedCount);
    }

    /**
     * Try to claim lease for an execution using optimistic locking.
     * Updates lease_owner and lease_until atomically.
     *
     * @param executionId Execution ID to claim
     * @param brokerId Broker ID claiming the lease
     * @return true if claim succeeded, false otherwise
     */
    private boolean tryClaimLease(String executionId, String brokerId) {
        try {
            LocalDateTime leaseUntil = LocalDateTime.now().plusMinutes(5);
            persistentRepository.updateState(executionId, ExecutionState.RUNNING, null, brokerId, leaseUntil);
            return true;
        } catch (Exception e) {
            log.warn("[RECOVERY] Failed to claim lease for execution={}: {}", executionId, e.getMessage());
            return false;
        }
    }

    /**
     * Handle timeout callback for recovered executions
     */
    private void onExecutionTimeout(String executionId) {
        ExecutionInfo info = executionStateStore.get(executionId);
        if (info == null || info.getState().isTerminal()) {
            return;
        }

        log.warn("[RECOVERY-TIMEOUT] Recovered execution timed out: executionId={}", executionId);

        info.setState(ExecutionState.TIMEOUT);
        info.setEndTime(System.currentTimeMillis());

        // Remove from memory and DB
        executionStateStore.remove(executionId);
        persistentRepository.remove(executionId);
    }
}
