package io.fluxion.server.core.job.query;

import io.limbo.cqrs.core.queryhandling.IQuery;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class JobRunningByWorkerQuery implements IQuery<JobRunningByWorkerQuery.Response> {

    private final String workerAddress;

    private final int limit;

    @Getter
    @AllArgsConstructor
    public static class Response {
        private final List<JobRunning> jobs;
    }

    @Getter
    @AllArgsConstructor
    public static class JobRunning {
        private final String jobId;
        private final int dispatchAttempt;
    }
}
