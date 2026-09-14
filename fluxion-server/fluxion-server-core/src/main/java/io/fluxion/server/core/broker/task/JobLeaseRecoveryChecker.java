package io.fluxion.server.core.broker.task;

import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.job.cmd.JobFailCmd;
import io.fluxion.server.core.job.query.JobExpiredLeaseQuery;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import io.limbo.utils.time.TimeUtils;

import java.util.concurrent.TimeUnit;

/** 接管 Broker 崩溃后仍处于运行态且 lease 已过期的 Job。 */
import io.limbo.cqrs.spring.gateway.CommandGateway;
import io.limbo.cqrs.spring.gateway.QueryGateway;
import javax.annotation.Resource;public class JobLeaseRecoveryChecker extends CoreTask {
    @Resource
    private CommandGateway commandGateway;
    @Resource
    private QueryGateway queryGateway;

    public JobLeaseRecoveryChecker() {
        super(0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        if (BrokerContext.broker() == null) {
            return;
        }
        JobExpiredLeaseQuery.Response response = queryGateway.query(new JobExpiredLeaseQuery(100, TimeUtils.currentLocalDateTime()));
        for (JobExpiredLeaseQuery.JobLease job : response.getJobs()) {
            commandGateway.send(new JobFailCmd(job.getJobId(), TimeUtils.currentLocalDateTime(), job.getDispatchAttempt(),
                "Broker lease expired", null));
        }
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.FIXED_DELAY;
    }
}
