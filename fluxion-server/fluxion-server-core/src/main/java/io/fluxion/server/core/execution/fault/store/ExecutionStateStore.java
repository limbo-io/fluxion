package io.fluxion.server.core.execution.fault.store;

import io.fluxion.server.core.execution.fault.ExecutionInfo;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 执行状态存储（内存实现）
 */
@Component
public class ExecutionStateStore {

    /**
     * 执行ID -> 执行信息
     */
    private final ConcurrentHashMap<String, ExecutionInfo> executions = new ConcurrentHashMap<>();

    /**
     * Worker ID -> 执行ID 集合（用于故障迁移）
     */
    private final ConcurrentHashMap<String, Set<String>> workerExecutions = new ConcurrentHashMap<>();

    /**
     * 添加执行
     */
    public void add(ExecutionInfo execution) {
        executions.put(execution.getExecutionId(), execution);
        // 只有当 workerId 不为空时才添加到 workerExecutions
        if (execution.getWorkerId() != null) {
            workerExecutions
                .computeIfAbsent(execution.getWorkerId(), k -> ConcurrentHashMap.newKeySet())
                .add(execution.getExecutionId());
        }
    }

    /**
     * 获取执行信息
     */
    public ExecutionInfo get(String executionId) {
        return executions.get(executionId);
    }

    /**
     * 移除执行
     */
    public void remove(String executionId) {
        ExecutionInfo removed = executions.remove(executionId);
        if (removed != null && removed.getWorkerId() != null) {
            Set<String> workerExecs = workerExecutions.get(removed.getWorkerId());
            if (workerExecs != null) {
                workerExecs.remove(executionId);
            }
        }
    }

    /**
     * 获取 Worker 所有执行中的任务
     */
    public List<ExecutionInfo> getActiveByWorker(String workerId) {
        return workerExecutions
            .getOrDefault(workerId, Collections.emptySet())
            .stream()
            .map(executions::get)
            .filter(e -> e != null && e.getState().isActive())
            .collect(Collectors.toList());
    }

    /**
     * 获取活跃执行数量
     */
    public int getActiveCount() {
        return (int) executions.values().stream()
            .filter(e -> e.getState().isActive())
            .count();
    }
}