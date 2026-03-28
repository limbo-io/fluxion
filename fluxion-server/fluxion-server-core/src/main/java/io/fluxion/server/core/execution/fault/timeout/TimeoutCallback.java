package io.fluxion.server.core.execution.fault.timeout;

/**
 * 超时回调接口
 */
@FunctionalInterface
public interface TimeoutCallback {

    /**
     * 超时触发时的回调
     * @param executionId 执行ID
     */
    void onTimeout(String executionId);
}