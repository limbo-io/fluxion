package io.fluxion.test.core.broker;

import io.fluxion.remote.core.client.server.ClientServer;
import io.fluxion.remote.core.constants.Protocol;
import io.fluxion.server.core.broker.Broker;
import io.fluxion.server.core.broker.BrokerManger;
import io.fluxion.server.core.schedule.ScheduleLeaseProperties;
import org.junit.jupiter.api.Test;

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
            leaseProperties(), coreExecutor);

        broker.start();

        // Core tasks: 1 FIXED_RATE (ScheduleLoader) + 9 FIXED_DELAY.
        verify(coreExecutor, times(1)).scheduleAtFixedRate(any(Runnable.class), eq(0L), anyLong(), any(TimeUnit.class));
        verify(coreExecutor, times(9)).scheduleWithFixedDelay(any(Runnable.class), eq(0L), anyLong(), any(TimeUnit.class));
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

}
