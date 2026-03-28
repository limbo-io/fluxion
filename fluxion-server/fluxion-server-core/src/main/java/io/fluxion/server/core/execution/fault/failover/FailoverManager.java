package io.fluxion.server.core.execution.fault.failover;

import io.fluxion.server.core.execution.fault.ExecutionInfo;

import java.util.List;

/**
 * 故障迁移管理器接口
 */
public interface FailoverManager {

    /**
     * 标记 Worker 故障，触发任务迁移
     * @param workerId 故障 Worker ID
     * @return 需要迁移的任务列表
     */
    List<ExecutionInfo> markWorkerFailed(String workerId);

    /**
     * 迁移任务到其他 Worker
     * @param execution 需要迁移的任务
     * @return 迁移结果
     */
    FailoverResult migrate(ExecutionInfo execution);
}