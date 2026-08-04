package io.fluxion.server.core.job.cmd;

import io.limbo.cqrs.core.command.ICommand;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/** Renews one active Job lease through the Job command service. */
@Getter
@AllArgsConstructor
public class JobLeaseRenewCmd implements ICommand<Boolean> {

    private final String jobId;
    private final String brokerId;
    private final int dispatchAttempt;
    private final LocalDateTime leaseUntil;
}
