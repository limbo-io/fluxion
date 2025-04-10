package io.fluxion.server.core.execution;

import io.fluxion.server.core.context.RunContext;
import io.fluxion.server.core.executor.option.RetryOption;
import io.fluxion.server.core.job.Job;

import java.time.LocalDateTime;

/**
 * @author Devil
 */
public interface Executable {

    String id();

    String version();

    ExecutableType type();

    void execute(RunContext context);

    boolean success(String refId, String executionId, LocalDateTime time);

    RetryOption retryOption(String refId);

    /**
     * 执行失败是否继续
     * true  会继续执行后续作业
     * false 终止环节
     */
    default boolean skipWhenFail(String refId) {
        return false;
    }

    Job job(String refId, String executionId, LocalDateTime triggerAt);

}
