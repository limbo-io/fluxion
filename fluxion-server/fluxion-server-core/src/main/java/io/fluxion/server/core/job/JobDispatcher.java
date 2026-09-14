/*
 * Copyright 2025-2030 Fluxion Team (https://github.com/Fluxion-io).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.fluxion.server.core.job;

import io.fluxion.common.thread.CommonThreadPool;
import io.fluxion.server.core.job.cmd.JobRunCmd;
import io.fluxion.server.infrastructure.concurrent.LoggingTask;
import io.limbo.cqrs.spring.gateway.CommandGateway;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;

/**
 * 在创建 Job 的事务提交后再异步下发，避免 Worker 先收到 Job 而数据库记录尚未可见。
 */
@Component
public class JobDispatcher {

    private static CommandGateway commandGateway;

    @Resource
    public void setCommandGateway(CommandGateway commandGateway) {
        JobDispatcher.commandGateway = commandGateway;
    }

    private JobDispatcher() {
    }

    public static void dispatchAfterCommit(Job job) {
        Runnable dispatch = () -> CommonThreadPool.IO.submit(new LoggingTask(() -> commandGateway.send(new JobRunCmd(job))));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatch.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                dispatch.run();
            }
        });
    }
}
