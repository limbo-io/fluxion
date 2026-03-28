package io.fluxion.server.core.execution.fault;

import lombok.Getter;

/**
 * 执行状态枚举
 */
@Getter
public enum ExecutionState {

    PENDING,        // 待分发
    DISPATCHED,     // 已分发
    RUNNING,        // 执行中
    SUCCEEDED,      // 成功
    FAILED,         // 失败（不可重试）
    RETRYING,       // 重试中
    TIMEOUT,        // 超时
    MIGRATING,      // 迁移中（Worker 故障）
    CANCELLED;      // 已取消

    /**
     * 是否为活跃状态（可继续处理）
     */
    public boolean isActive() {
        return this == PENDING || this == DISPATCHED || this == RUNNING
            || this == RETRYING || this == MIGRATING;
    }

    /**
     * 是否为终态
     */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == TIMEOUT || this == CANCELLED;
    }
}