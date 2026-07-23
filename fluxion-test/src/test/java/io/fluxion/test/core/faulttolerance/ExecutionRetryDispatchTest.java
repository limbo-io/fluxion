package io.fluxion.test.core.faulttolerance;

import io.fluxion.server.core.execution.fault.ExecutionInfo;
import io.fluxion.server.core.execution.fault.ExecutionState;
import io.fluxion.server.core.execution.fault.store.ExecutionStateStore;
import io.fluxion.server.core.execution.service.ExecutionRetryService;
import io.fluxion.server.core.job.cmd.JobRetryCmd;
import io.fluxion.server.infrastructure.schedule.scheduler.DelayedTaskScheduler;
import io.fluxion.server.infrastructure.schedule.task.DelayedTask;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

class ExecutionRetryDispatchTest {

    @Test
    void shouldDispatchRetryForTheExecutionJob() throws Exception {
        ExecutionStateStore store = new ExecutionStateStore();
        store.add(ExecutionInfo.builder()
            .executionId("execution-1")
            .jobId("job-1")
            .retryCount(2)
            .state(ExecutionState.RETRYING)
            .build());
        DelayedTaskScheduler scheduler = mock(DelayedTaskScheduler.class);
        CapturingExecutionRetryService service = new CapturingExecutionRetryService();
        setField(service, "executionStateStore", store);
        setField(service, "delayedTaskScheduler", scheduler);

        service.scheduleRetry("execution-1", Duration.ZERO);

        ArgumentCaptor<DelayedTask> taskCaptor = ArgumentCaptor.forClass(DelayedTask.class);
        verify(scheduler).schedule(taskCaptor.capture());
        taskCaptor.getValue().run();

        assertEquals("job-1", service.command.getJobId());
        assertEquals(2, service.command.getRetryTimes());
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getSuperclass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class CapturingExecutionRetryService extends ExecutionRetryService {

        private JobRetryCmd command;

        @Override
        protected void sendJobRetry(String jobId, int retryTimes) {
            command = new JobRetryCmd(jobId, retryTimes);
        }
    }
}
