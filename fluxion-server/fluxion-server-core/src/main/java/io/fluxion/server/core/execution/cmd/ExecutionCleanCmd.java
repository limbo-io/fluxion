/*
 * Copyright 2025-2030 Fluxion Team (https://github.com/Fluxion-io).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package io.fluxion.server.core.execution.cmd;

import io.limbo.cqrs.core.commandhandling.VoidCommand;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ExecutionCleanCmd implements VoidCommand {
    private LocalDateTime endAt;
}
