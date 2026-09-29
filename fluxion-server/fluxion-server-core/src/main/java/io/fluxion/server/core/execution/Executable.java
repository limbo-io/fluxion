package io.fluxion.server.core.execution;

import io.fluxion.server.core.job.Job;

import java.time.Instant;

/**
 * @author Devil
 */
public interface Executable {

    String id();

    String version();

    ExecutableType type();

    void execute(Execution execution);

    boolean success(String executionId, String refId, Instant time);

    boolean fail(String executionId, String refId, Instant time);

    Job.Config config(String refId);

}
