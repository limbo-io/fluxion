package io.fluxion.server.core.execution.fault.timeout;

import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 基于时间轮的超时管理器实现
 */
@Slf4j
@Component
public class TimingWheelTimeoutManager implements TimeoutManager {

    private final HashedWheelTimer timer;
    private final ConcurrentHashMap<String, Timeout> timeouts = new ConcurrentHashMap<>();

    public TimingWheelTimeoutManager() {
        this.timer = new HashedWheelTimer(
            r -> new Thread(r, "fault-tolerance-timeout"),
            100, TimeUnit.MILLISECONDS,
            512
        );
    }

    @Override
    public void addTimeout(String executionId, Duration timeout, TimeoutCallback callback) {
        TimerTask task = new TimerTask() {
            @Override
            public void run(Timeout timeout) {
                timeouts.remove(executionId);
                try {
                    log.warn("[FAULT-TIMEOUT] Execution timed out: executionId={}", executionId);
                    callback.onTimeout(executionId);
                } catch (Exception e) {
                    log.error("[FAULT-TIMEOUT] Error in timeout callback for executionId={}", executionId, e);
                }
            }
        };

        Timeout t = timer.newTimeout(task, timeout.toMillis(), TimeUnit.MILLISECONDS);
        timeouts.put(executionId, t);

        log.debug("[FAULT-TIMEOUT] Added timeout watch: executionId={}, timeout={}ms",
            executionId, timeout.toMillis());
    }

    @Override
    public void cancelTimeout(String executionId) {
        Timeout t = timeouts.remove(executionId);
        if (t != null) {
            t.cancel();
            log.debug("[FAULT-TIMEOUT] Cancelled timeout watch: executionId={}", executionId);
        }
    }
}