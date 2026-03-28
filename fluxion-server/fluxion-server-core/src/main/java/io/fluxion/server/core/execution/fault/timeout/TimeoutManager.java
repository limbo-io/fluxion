package io.fluxion.server.core.execution.fault.timeout;

import java.time.Duration;

/**
 * 超时管理器接口
 */
public interface TimeoutManager {

    /**
     * 添加超时监控
     * @param executionId 执行ID
     * @param timeout 超时时间
     * @param callback 超时回调
     */
    void addTimeout(String executionId, Duration timeout, TimeoutCallback callback);

    /**
     * 取消超时监控
     * @param executionId 执行ID
     */
    void cancelTimeout(String executionId);
}