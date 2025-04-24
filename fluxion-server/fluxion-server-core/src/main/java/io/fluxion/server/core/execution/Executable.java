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

    boolean success(Job job, LocalDateTime time);

    boolean fail(Job job, LocalDateTime time);

    RetryOption retryOption(String refId);

    /**
     * 返回一个 ref 关联类型的job
     */
    Job newRefJob(String refId);

}
