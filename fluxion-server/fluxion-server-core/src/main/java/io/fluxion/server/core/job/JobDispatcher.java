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
import io.limbo.cqrs.core.commandhandling.Cmd;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 在创建 Job 的事务提交后再异步下发，避免 Worker 先收到 Job 而数据库记录尚未可见。
 * 通过 {@link Commands} 静态门面发送命令，无需手动注入依赖。
 */
public final class JobDispatcher {

    private JobDispatcher() {
    }

    public static void dispatchAfterCommit(Job job) {
        Runnable dispatch = () -> CommonThreadPool.IO.submit(new LoggingTask(() -> Cmd.send(new JobRunCmd(job))));
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
