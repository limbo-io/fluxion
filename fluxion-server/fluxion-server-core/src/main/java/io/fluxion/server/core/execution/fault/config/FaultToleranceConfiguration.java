package io.fluxion.server.core.execution.fault.config;

import io.fluxion.server.core.execution.fault.DefaultFaultToleranceCoordinator;
import io.fluxion.server.core.execution.fault.FaultToleranceCoordinator;
import io.fluxion.server.core.execution.fault.failover.DefaultFailoverManager;
import io.fluxion.server.core.execution.fault.failover.FailoverManager;
import io.fluxion.server.core.execution.fault.retry.ExponentialBackoffRetryStrategy;
import io.fluxion.server.core.execution.fault.retry.RetryStrategy;
import io.fluxion.server.core.execution.fault.store.ExecutionStateStore;
import io.fluxion.server.core.execution.fault.timeout.TimingWheelTimeoutManager;
import io.fluxion.server.core.execution.fault.timeout.TimeoutManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 容错自动配置
 */
@Configuration
public class FaultToleranceConfiguration {

    @Bean
    public RetryStrategy retryStrategy(FaultToleranceProperties properties) {
        FaultToleranceProperties.RetryConfig retry = properties.getRetry();
        return new ExponentialBackoffRetryStrategy(
            retry.getMaxRetries(),
            retry.getInitialDelay(),
            retry.getMaxDelay(),
            retry.getMultiplier(),
            retry.getJitter()
        );
    }

    @Bean
    public TimeoutManager timeoutManager() {
        return new TimingWheelTimeoutManager();
    }

    @Bean
    public FailoverManager failoverManager(ExecutionStateStore stateStore) {
        return new DefaultFailoverManager(stateStore);
    }

    @Bean
    public FaultToleranceCoordinator faultToleranceCoordinator(
            ExecutionStateStore stateStore,
            RetryStrategy retryStrategy,
            TimeoutManager timeoutManager,
            FailoverManager failoverManager) {
        return new DefaultFaultToleranceCoordinator(
            stateStore, retryStrategy, timeoutManager, failoverManager
        );
    }
}