package io.fluxion.server.core.execution.fault.failover;

import io.fluxion.server.core.execution.fault.ExecutionInfo;
import io.fluxion.server.core.execution.fault.ExecutionState;
import io.fluxion.server.core.execution.fault.store.ExecutionStateStore;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 默认故障迁移管理器实现
 */
@Slf4j
public class DefaultFailoverManager implements FailoverManager {

    private final ExecutionStateStore stateStore;

    public DefaultFailoverManager(ExecutionStateStore stateStore) {
        this.stateStore = stateStore;
    }

    @Override
    public List<ExecutionInfo> markWorkerFailed(String workerId) {
        log.warn("[FAULT-MIGRATE] Marking worker as failed: workerId={}", workerId);

        List<ExecutionInfo> activeExecutions = stateStore.getActiveByWorker(workerId);

        // 更新状态为 MIGRATING
        for (ExecutionInfo info : activeExecutions) {
            info.setState(ExecutionState.MIGRATING);
            log.info("[FAULT-MIGRATE] Execution marked for migration: executionId={}", info.getExecutionId());
        }

        return activeExecutions;
    }

    @Override
    public FailoverResult migrate(ExecutionInfo execution) {
        log.info("[FAULT-MIGRATE] Attempting to migrate execution: executionId={}",
            execution.getExecutionId());

        // TODO: 实际实现需要选择新的 Worker 并重新分发任务
        // 当前返回失败，待后续集成 WorkerSelector
        return FailoverResult.failed(execution.getExecutionId(), "Worker selection not implemented");
    }
}