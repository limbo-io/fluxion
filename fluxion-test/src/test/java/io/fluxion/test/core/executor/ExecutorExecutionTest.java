package io.fluxion.test.core.executor;

import io.fluxion.server.core.execution.ExecutableType;
import io.fluxion.server.core.execution.Execution;
import io.fluxion.server.core.executor.Executor;
import io.fluxion.server.core.job.cmd.JobsCreateCmd;
import io.limbo.cqrs.core.command.CommandBus;
import io.limbo.cqrs.spring.command.Cmd;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExecutorExecutionTest {

    @AfterEach
    void clearCommandBus() throws Exception {
        commandBusField().set(null, null);
    }

    @Test
    void shouldCreateJobForTheCurrentExecution() throws Exception {
        CopyOnWriteArrayList<Object> commands = new CopyOnWriteArrayList<>();
        CommandBus commandBus = mock(CommandBus.class);
        when(commandBus.execute(any(), any())).thenAnswer(invocation -> {
            commands.add(invocation.getArgument(0));
            return null;
        });
        commandBusField().set(null, commandBus);

        newExecutor().execute(new Execution("execution-1", null, "executor-1", "v1", ExecutableType.EXECUTOR));

        JobsCreateCmd command = commands.stream()
            .filter(JobsCreateCmd.class::isInstance)
            .map(JobsCreateCmd.class::cast)
            .findFirst()
            .orElseThrow();
        assertEquals("execution-1", command.getJobs().get(0).getExecutionId());
    }

    private Executor newExecutor() {
        return Executor.of("executor-1", "v1", null, null, null);
    }

    private Field commandBusField() throws Exception {
        Field field = Cmd.class.getDeclaredField("BUS");
        field.setAccessible(true);
        return field;
    }
}
