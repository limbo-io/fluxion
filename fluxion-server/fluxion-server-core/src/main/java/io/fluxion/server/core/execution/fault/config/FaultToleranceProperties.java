package io.fluxion.server.core.execution.fault.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 容错配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "fluxion.fault-tolerance")
public class FaultToleranceProperties {

    /**
     * 重试配置
     */
    private RetryConfig retry = new RetryConfig();

    /**
     * 超时配置
     */
    private TimeoutConfig timeout = new TimeoutConfig();

    /**
     * 故障迁移配置
     */
    private FailoverConfig failover = new FailoverConfig();

    /**
     * 执行恢复配置
     */
    private RecoveryConfig recovery = new RecoveryConfig();

    @Data
    public static class RetryConfig {
        private int maxRetries = 5;
        private Duration initialDelay = Duration.ofSeconds(1);
        private Duration maxDelay = Duration.ofSeconds(30);
        private double multiplier = 2.0;
        private double jitter = 0.1;
    }

    @Data
    public static class TimeoutConfig {
        private Duration defaultTimeout = Duration.ofMinutes(30);
    }

    @Data
    public static class FailoverConfig {
        private boolean enabled = true;
        private Duration workerOfflineTimeout = Duration.ofSeconds(10);
        private int maxMigrations = 3;
    }

    @Data
    public static class RecoveryConfig {
        /**
         * 执行恢复阈值：超过此时间的过期租约可被恢复
         * Priority: Worker offline < task timeout < broker recovery threshold
         */
        private Duration recoveryThreshold = Duration.ofMinutes(30);
    }
}