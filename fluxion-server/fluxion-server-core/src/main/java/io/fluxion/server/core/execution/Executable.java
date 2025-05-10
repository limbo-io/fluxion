package io.fluxion.server.core.execution;

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

    boolean success(String executionId, String refId, LocalDateTime time);

    boolean fail(String executionId, String refId, LocalDateTime time);

    Job.Config config(String refId);

}
