/*
 * Copyright 2025-2030 Fluxion Team (https://github.com/Fluxion-io).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package io.fluxion.server.core.broker.task;

import io.fluxion.server.core.execution.cmd.ExecutionsLoadCmd;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

import io.limbo.cqrs.spring.gateway.CommandGateway;
import javax.annotation.Resource;/**
 * Preloads due PENDING Executions into the Broker time wheel.
 */
@Slf4j
public class ExecutionLoader extends CoreTask {
    @Resource
    private CommandGateway commandGateway;

    public ExecutionLoader() {
        super(0, 1, TimeUnit.MINUTES);
    }

    @Override
    public void run() {
        try {
            commandGateway.send(new ExecutionsLoadCmd());
        } catch (Exception e) {
            log.error("[{}] execute fail", getClass().getSimpleName(), e);
        }
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.FIXED_DELAY;
    }
}
