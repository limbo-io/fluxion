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

    void execute(Execution execution);

    boolean success(Job job, LocalDateTime time);

    boolean fail(Job job, LocalDateTime time);

    Job.Config config(String refId);

}
