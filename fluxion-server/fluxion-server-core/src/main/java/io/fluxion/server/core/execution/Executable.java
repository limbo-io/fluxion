package io.fluxion.server.core.execution;

import java.time.LocalDateTime;

/**
 * @author Devil
 * @date 2025/1/12
 */
public interface Executable {

    void execute(LocalDateTime triggerAt);
}
