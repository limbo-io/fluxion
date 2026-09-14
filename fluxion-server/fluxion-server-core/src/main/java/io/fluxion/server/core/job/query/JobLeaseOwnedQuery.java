package io.fluxion.server.core.job.query;

import io.limbo.cqrs.core.queryhandling.IQuery;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** Queries active Job leases owned by one Broker. */
@Getter
@AllArgsConstructor
public class JobLeaseOwnedQuery implements IQuery<JobLeaseOwnedQuery.Response> {

    private final String brokerId;
    private final int limit;
    private final String lastJobId;

    @Getter
    @AllArgsConstructor
    public static class Response {
        private final List<JobLease> jobs;
    }

    @Getter
    @AllArgsConstructor
    public static class JobLease {
        private final String jobId;
        private final int dispatchAttempt;
    }
}
