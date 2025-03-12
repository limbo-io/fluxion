package io.fluxion.server.core.execution;

import io.fluxion.server.core.context.RunContext;

/**
 * @author Devil
 * @date 2025/1/12
 */
public interface Executable {

    void execute(RunContext context);
}
