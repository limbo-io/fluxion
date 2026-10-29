package io.fluxion.test.unit.broker;

import io.fluxion.remote.core.client.server.ClientServer;
import io.fluxion.remote.core.constants.Protocol;
import io.fluxion.server.core.broker.Broker;
import io.fluxion.server.core.broker.BrokerManger;
import io.fluxion.server.core.schedule.ScheduleLeaseProperties;
import io.fluxion.server.core.worker.WorkerLivenessProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;

class BrokerLifecycleTest {

    @Test
    void shouldRegisterAllCoreTasksWhenBrokerStarts() throws Exception {
        BrokerManger brokerManger = mock(BrokerManger.class);
        ClientServer clientServer = mock(ClientServer.class);
        ScheduledExecutorService coreExecutor = mock(ScheduledExecutorService.class);
        Broker broker = new Broker(Protocol.HTTP, "127.0.0.1", 19785, brokerManger, clientServer,
            leaseProperties(), workerLivenessProperties(), coreExecutor);

        broker.start();

        // Core tasks: 1 FIXED_RATE (ScheduleLoader) + 9 FIXED_DELAY.
        verify(coreExecutor, times(1)).scheduleAtFixedRate(any(Runnable.class), eq(0L), anyLong(), any(TimeUnit.class));
        verify(coreExecutor, times(9)).scheduleWithFixedDelay(any(Runnable.class), eq(0L), anyLong(), any(TimeUnit.class));
        ArgumentCaptor<Runnable> scheduled = ArgumentCaptor.forClass(Runnable.class);
        verify(coreExecutor).scheduleAtFixedRate(scheduled.capture(), eq(0L), anyLong(), any(TimeUnit.class));
        assertThat(ReflectionTestUtils.getField(scheduled.getValue(), "task").getClass().getSimpleName())
            .isEqualTo("ScheduleLoader");
        verify(coreExecutor, times(9)).scheduleWithFixedDelay(scheduled.capture(), eq(0L), anyLong(), any(TimeUnit.class));
        assertThat(scheduled.getAllValues().stream()
            .map(task -> ReflectionTestUtils.getField(task, "task").getClass().getSimpleName()))
            .containsExactlyInAnyOrder("ScheduleLoader", "ExecutionLoader", "BucketChecker", "DataCleaner",
                "WorkerChecker", "JobUnRunChecker", "JobRetryChecker", "JobTimeoutChecker",
                "JobLeaseRecoveryChecker", "JobLeaseRenewChecker");
        verify(brokerManger).start();
        verify(clientServer).start();
    }

    private ScheduleLeaseProperties leaseProperties() {
        ScheduleLeaseProperties properties = new ScheduleLeaseProperties();
        properties.setDuration(15);
        properties.setRenewInterval(10);
        properties.setReclaimInterval(5);
        return properties;
    }

    private WorkerLivenessProperties workerLivenessProperties() {
        return new WorkerLivenessProperties();
    }

}
