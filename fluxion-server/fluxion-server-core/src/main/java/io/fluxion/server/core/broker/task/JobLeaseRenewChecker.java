package io.fluxion.server.core.broker.task;

import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.job.cmd.JobLeaseRenewCmd;
import io.fluxion.server.core.job.query.JobLeaseOwnedQuery;
import io.fluxion.server.core.job.service.JobLeaseService;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import io.limbo.utils.time.TimeUtils;

import java.util.concurrent.TimeUnit;

/** Keeps active Job leases alive while the current Broker awaits Worker completion. */
import io.limbo.cqrs.spring.gateway.CommandGateway;
import io.limbo.cqrs.spring.gateway.QueryGateway;
import javax.annotation.Resource;public class JobLeaseRenewChecker extends CoreTask {
    @Resource
    private CommandGateway commandGateway;
    @Resource
    private QueryGateway queryGateway;

    public JobLeaseRenewChecker() {
        super(0, 10, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        if (BrokerContext.broker() == null) {
            return;
        }
        String brokerId = BrokerContext.broker().id();
        String lastJobId = "";
        JobLeaseOwnedQuery.Response response;
        do {
            response = queryGateway.query(new JobLeaseOwnedQuery(brokerId, 100, lastJobId));
            for (JobLeaseOwnedQuery.JobLease job : response.getJobs()) {
                commandGateway.send(new JobLeaseRenewCmd(job.getJobId(), brokerId, job.getDispatchAttempt(),
                    TimeUtils.currentLocalDateTime().plusSeconds(JobLeaseService.LEASE_DURATION_SECONDS)));
            }
            if (!response.getJobs().isEmpty()) {
                lastJobId = response.getJobs().get(response.getJobs().size() - 1).getJobId();
            }
        } while (response.getJobs().size() >= 100);
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.FIXED_DELAY;
    }
}
