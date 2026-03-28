package io.fluxion.server.core.execution.fault;

/**
 * 容错协调器接口 - 统一管理任务执行容错
 */
public interface FaultToleranceCoordinator {

    /**
     * 注册任务执行
     * @param execution 任务执行信息
     * @return 注册结果
     */
    ExecutionRegistration register(ExecutionInfo execution);

    /**
     * 任务执行完成（成功或失败）
     * @param executionId 执行ID
     * @param result 执行结果
     */
    void complete(String executionId, ExecutionResult result);

    /**
     * Worker 心跳更新
     * @param workerId Worker ID
     */
    void onWorkerHeartbeat(String workerId);

    /**
     * Worker 下线通知
     * @param workerId Worker ID
     */
    void onWorkerOffline(String workerId);
}