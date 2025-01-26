package io.fluxion.server.core.execution;

import io.fluxion.server.core.context.RunContext;

/**
 * @author Devil
 * @date 2025/1/12
 */
public interface Executable {
    // todo @d 上下文通过线程获取和传递
    void execute(RunContext context);
}
