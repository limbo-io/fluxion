/*
 * Copyright 2025-2030 Fluxion Team (https://github.com/Fluxion-io).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package io.fluxion.server.core.broker.task;

import io.fluxion.server.core.execution.cmd.ExecutionsLoadCmd;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import io.limbo.cqrs.spring.command.Cmd;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * Preloads due PENDING Executions into the Broker time wheel.
 */
@Slf4j
public class ExecutionLoader extends CoreTask {

    public ExecutionLoader() {
        super(0, 1, TimeUnit.MINUTES);
    }

    @Override
    public void run() {
        try {
            Cmd.send(new ExecutionsLoadCmd());
        } catch (Exception e) {
            log.error("[{}] execute fail", getClass().getSimpleName(), e);
        }
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.FIXED_DELAY;
    }
}
