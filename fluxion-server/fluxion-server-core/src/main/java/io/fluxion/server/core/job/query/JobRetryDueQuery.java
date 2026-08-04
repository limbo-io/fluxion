package io.fluxion.server.core.job.query;

import io.limbo.cqrs.core.query.IQuery;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class JobRetryDueQuery implements IQuery<JobRetryDueQuery.Response> {

    private final int limit;

    private final LocalDateTime now;

    @Getter
    @AllArgsConstructor
    public static class Response {
        private final List<JobRetry> jobs;
    }

    @Getter
    @AllArgsConstructor
    public static class JobRetry {
        private final String jobId;
        private final int retryTimes;
    }
}
