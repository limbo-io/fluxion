package io.fluxion.test.core.executor;

import io.fluxion.server.core.execution.ExecutableType;
import io.fluxion.server.core.execution.Execution;
import io.fluxion.server.core.executor.Executor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * 现已被禁用：旧实现通过反射 {@code Cmd.BUS} 静态字段注入 mock 的 CommandBus。
 * 新版 gateway API 使用注入的 {@code CommandGateway}，{@code Executor} 由工厂方法创建，
 * 不再适合静态反射注入。待 {@code Executor}/{@code Workflow} 等 POJO 的依赖注入方式重构后重新启用。
 */
@Disabled
class ExecutorExecutionTest {

    @Test
    void shouldCreateJobForTheCurrentExecution() {
        Executor executor = Executor.of("executor-1", "v1", null, null, null);
        executor.execute(new Execution("execution-1", null, "executor-1", "v1", ExecutableType.EXECUTOR));
    }
}
