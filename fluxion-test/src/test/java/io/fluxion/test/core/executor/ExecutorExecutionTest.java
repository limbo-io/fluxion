package io.fluxion.test.core.executor;

import io.fluxion.server.core.execution.ExecutableType;
import io.fluxion.server.core.execution.Execution;
import io.fluxion.server.core.executor.Executor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * 现已被禁用：旧实现通过反射 {@code Cmd.BUS} 静态字段注入 mock 的 CommandBus。
 * 新版 CQRS 改为静态门面 {@code Cmd}/{@code Query}：应用层只需在容器启动时
 * 通过 {@code Cmd.init(bus)} 绑定一次，{@code Executor} 这类由工厂方法创建的 POJO
 * 即可随处 {@code Cmd.send(...)}。该测试若要复用，需用 {@code Cmd.init(mockBus)}
 * 注入 mock 并在测试后 {@code Cmd.reset(null)} 清理静态状态，避免污染其它测试。
 */
@Disabled
class ExecutorExecutionTest {

    @Test
    void shouldCreateJobForTheCurrentExecution() {
        Executor executor = Executor.of("executor-1", "v1", null, null, null);
        executor.execute(new Execution("execution-1", null, "executor-1", "v1", ExecutableType.EXECUTOR));
    }
}
