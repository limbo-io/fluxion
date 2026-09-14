package io.fluxion.server.core.broker.task;

import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.job.cmd.JobRetryCmd;
import io.fluxion.server.core.job.query.JobRetryDueQuery;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import io.limbo.utils.time.TimeUtils;

import java.util.concurrent.TimeUnit;

import io.limbo.cqrs.spring.gateway.CommandGateway;
import io.limbo.cqrs.spring.gateway.QueryGateway;
import javax.annotation.Resource;

/**
* 从持久化 next_retry_at 重新触发到期 Job；Broker 重启不依赖旧 JVM 定时器。
 */
public class JobRetryChecker extends CoreTask {
    @Resource
    private CommandGateway commandGateway;
    @Resource
    private QueryGateway queryGateway;

    public JobRetryChecker() {
        super(0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        if (BrokerContext.broker() == null) {
            return;
        }
        JobRetryDueQuery.Response response = queryGateway.query(new JobRetryDueQuery(100, TimeUtils.currentLocalDateTime()));
        for (JobRetryDueQuery.JobRetry job : response.getJobs()) {
            commandGateway.send(new JobRetryCmd(job.getJobId(), job.getRetryTimes()));
        }
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.FIXED_DELAY;
    }
}
