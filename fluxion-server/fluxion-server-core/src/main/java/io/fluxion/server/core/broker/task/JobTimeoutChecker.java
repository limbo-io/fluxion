package io.fluxion.server.core.broker.task;

import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.job.cmd.JobFailCmd;
import io.fluxion.server.core.job.query.JobTimeoutDueQuery;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import io.limbo.cqrs.spring.command.Cmd;
import io.limbo.cqrs.spring.query.Query;
import io.limbo.utils.time.TimeUtils;

import java.util.concurrent.TimeUnit;

/** 按 Job 的 timeout_at 和 dispatch_attempt 处理超时。 */
public class JobTimeoutChecker extends CoreTask {

    public JobTimeoutChecker() {
        super(0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        if (BrokerContext.broker() == null) {
            return;
        }
        JobTimeoutDueQuery.Response response = Query.query(new JobTimeoutDueQuery(100, TimeUtils.currentLocalDateTime()));
        for (JobTimeoutDueQuery.JobTimeout job : response.getJobs()) {
            Cmd.send(new JobFailCmd(job.getJobId(), TimeUtils.currentLocalDateTime(), job.getDispatchAttempt(),
                "Job timeout", null));
        }
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.FIXED_DELAY;
    }
}
