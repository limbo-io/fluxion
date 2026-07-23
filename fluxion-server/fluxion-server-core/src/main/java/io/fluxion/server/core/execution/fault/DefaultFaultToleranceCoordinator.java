package io.fluxion.server.core.execution.fault;

import io.fluxion.server.core.execution.cmd.ExecutionRetryScheduleCmd;
import io.fluxion.server.core.execution.fault.config.FaultToleranceProperties;
import io.fluxion.server.core.execution.fault.failover.FailoverManager;
import io.fluxion.server.core.execution.fault.retry.RetryContext;
import io.fluxion.server.core.execution.fault.retry.RetryStrategy;
import io.fluxion.server.core.execution.fault.store.ExecutionStateStore;
import io.fluxion.server.core.execution.fault.store.PersistentExecutionStateRepository;
import io.fluxion.server.core.execution.fault.timeout.TimeoutManager;
import io.limbo.cqrs.spring.command.Cmd;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 默认容错协调器实现
 */
@Slf4j
public class DefaultFaultToleranceCoordinator implements FaultToleranceCoordinator {

    private final ExecutionStateStore stateStore;
    private final RetryStrategy retryStrategy;
    private final TimeoutManager timeoutManager;
    private final FailoverManager failoverManager;

    @Resource
    private PersistentExecutionStateRepository persistentRepository;

    private final FaultToleranceProperties properties;

    public DefaultFaultToleranceCoordinator(
            ExecutionStateStore stateStore,
            RetryStrategy retryStrategy,
            TimeoutManager timeoutManager,
            FailoverManager failoverManager,
            FaultToleranceProperties properties) {
        this.stateStore = stateStore;
        this.retryStrategy = retryStrategy;
        this.timeoutManager = timeoutManager;
        this.failoverManager = failoverManager;
        this.properties = properties;
    }

    private Duration getDefaultTimeout() {
        if (properties != null && properties.getTimeout() != null && properties.getTimeout().getDefaultTimeout() != null) {
            return properties.getTimeout().getDefaultTimeout();
        }
        return Duration.ofMinutes(30);
    }

    /**
     * 调度重试任务
     */
    protected void scheduleRetry(String executionId, Duration delay) {
        log.info("[FAULT-RETRY] Sending retry schedule command: executionId={}, delay={}ms",
            executionId, delay.toMillis());
        // 使用 CQRS 命令发送调度请求
        Cmd.send(new ExecutionRetryScheduleCmd(executionId, delay));
    }

    @Override
    public ExecutionRegistration register(ExecutionInfo execution) {
        log.info("[FAULT] Registering execution: executionId={}, workerId={}",
            execution.getExecutionId(), execution.getWorkerId());

        // 设置初始状态
        if (execution.getState() == null) {
            execution.setState(ExecutionState.RUNNING);
        }

        // 设置开始时间
        if (execution.getStartTime() == 0) {
            execution.setStartTime(System.currentTimeMillis());
        }

        // 设置超时时间戳
        Duration timeout = getDefaultTimeout();
        long timeoutTimestamp = System.currentTimeMillis() + timeout.toMillis();
        execution.setTimeoutTimestamp(timeoutTimestamp);

        // Persist to DB first (persistent source of truth)
        if (persistentRepository != null) {
            persistentRepository.register(execution);
        }

        // Then update in-memory state store (index for fast access)
        stateStore.add(execution);

        // 添加超时监控
        timeoutManager.addTimeout(execution.getExecutionId(), timeout, this::onExecutionTimeout);

        log.info("[FAULT] Execution registered successfully: executionId={}", execution.getExecutionId());

        return ExecutionRegistration.builder()
            .registered(true)
            .executionId(execution.getExecutionId())
            .timeoutTimestamp(timeoutTimestamp)
            .build();
    }

    @Override
    public void complete(String executionId, ExecutionResult result) {
        ExecutionInfo info = stateStore.get(executionId);
        if (info == null) {
            log.warn("[FAULT] Cannot complete unknown execution: executionId={}", executionId);
            return;
        }

        // 取消超时监控
        timeoutManager.cancelTimeout(executionId);

        if (result.isSuccess()) {
            // 成功完成
            info.setState(ExecutionState.SUCCEEDED);
            info.setEndTime(System.currentTimeMillis());
            
            // Remove from both DB and memory
            if (persistentRepository != null) {
                persistentRepository.remove(executionId);
            }
            stateStore.remove(executionId);
            
            log.info("[FAULT-SUCCESS] Execution completed successfully: executionId={}", executionId);
        } else {
            // 失败处理
            handleFailure(info, result);
        }
    }

    private void handleFailure(ExecutionInfo info, ExecutionResult result) {
        ErrorCategory category = result.getErrorCategory();
        if (category == null) {
            category = ErrorCategory.UNKNOWN;
        }

        info.setLastError(result.getError());

        RetryContext ctx = RetryContext.builder()
            .executionInfo(info)
            .error(result.getError())
            .errorCategory(category)
            .build();

        if (retryStrategy.shouldRetry(ctx, result.getError())) {
            // 可重试错误
            info.incrementRetryCount();
            info.setState(ExecutionState.RETRYING);

            Duration delay = retryStrategy.nextRetryDelay(ctx);
            if (delay != null) {
                log.info("[FAULT-RETRY] Scheduling retry: executionId={}, retryCount={}, delay={}ms",
                    info.getExecutionId(), info.getRetryCount(), delay.toMillis());
                // 调用重试调度服务
                scheduleRetry(info.getExecutionId(), delay);
            }
        } else {
            // 不可重试错误，标记失败
            info.setState(ExecutionState.FAILED);
            info.setEndTime(System.currentTimeMillis());
            
            // Remove from both DB and memory on terminal failure
            if (persistentRepository != null) {
                persistentRepository.remove(info.getExecutionId());
            }
            stateStore.remove(info.getExecutionId());
            
            log.error("[FAULT-FATAL] Execution failed permanently: executionId={}, errorCategory={}",
                info.getExecutionId(), category);
        }
    }

    @Override
    public void onWorkerHeartbeat(String workerId) {
        log.debug("[FAULT] Worker heartbeat received: workerId={}", workerId);
        // 可以在此更新 Worker 最后活跃时间
    }

    @Override
    public void onWorkerOffline(String workerId) {
        log.warn("[FAULT-MIGRATE] Worker offline, starting migration: workerId={}", workerId);

        List<ExecutionInfo> toMigrate = failoverManager.markWorkerFailed(workerId);

        for (ExecutionInfo info : toMigrate) {
            handleFailure(info, ExecutionResult.failed(
                new RuntimeException("Worker offline: " + workerId),
                ErrorCategory.TRANSIENT
            ));
        }
    }

    private void onExecutionTimeout(String executionId) {
        ExecutionInfo info = stateStore.get(executionId);
        if (info == null || info.getState().isTerminal()) {
            return;
        }

        log.warn("[FAULT-TIMEOUT] Execution timed out: executionId={}", executionId);

        // Apply same failure handling as regular failures (with retry policy)
        handleFailure(info, ExecutionResult.failed(
            new RuntimeException("Execution timed out after " + getDefaultTimeout()),
            ErrorCategory.TRANSIENT
        ));
    }
}
