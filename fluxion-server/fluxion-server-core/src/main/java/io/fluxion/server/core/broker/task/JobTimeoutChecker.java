package io.fluxion.server.core.broker.task;

import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.job.cmd.JobFailCmd;
import io.fluxion.server.core.job.query.JobTimeoutDueQuery;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import io.limbo.utils.time.TimeUtils;

import java.util.concurrent.TimeUnit;

/** 按 Job 的 timeout_at 和 dispatch_attempt 处理超时。 */
import io.limbo.cqrs.spring.gateway.CommandGateway;
import io.limbo.cqrs.spring.gateway.QueryGateway;
import javax.annotation.Resource;public class JobTimeoutChecker extends CoreTask {
    @Resource
    private CommandGateway commandGateway;
    @Resource
    private QueryGateway queryGateway;

    public JobTimeoutChecker() {
        super(0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        if (BrokerContext.broker() == null) {
            return;
        }
        JobTimeoutDueQuery.Response response = queryGateway.query(new JobTimeoutDueQuery(100, TimeUtils.currentLocalDateTime()));
        for (JobTimeoutDueQuery.JobTimeout job : response.getJobs()) {
            commandGateway.send(new JobFailCmd(job.getJobId(), TimeUtils.currentLocalDateTime(), job.getDispatchAttempt(),
                "Job timeout", null));
        }
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.FIXED_DELAY;
    }
}
