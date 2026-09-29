/*
 * Copyright 2025-2030 Fluxion Team (https://github.com/Fluxion-io).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxion.server.core.job.cmd;

import io.limbo.cqrs.core.commandhandling.ICommand;
import lombok.Getter;

import java.time.Instant;

/**
 * 将 Job 标记为 EXPIRED：Worker/本地因激活截止时间拒绝启动（见 CONTEXT.md「Job 派发结果」，联结 {@code rejected_deadline}）。
 * <p>
 * 与 {@link JobFailCmd} 不同：EXPIRED 是不可重试的终态，不进入 RetryOption。
 * 更新以 Job 当前状态与 dispatch attempt 作条件围栏，防止旧下发结果覆盖新尝试。
 *
 * @author Devil
 */
@Getter
public class JobExpiredCmd implements ICommand<Boolean> {

    private final String jobId;

    /**
     * 下发尝试次数，用于围栏；为空表示不经 Worker 下发（如工作流根节点本地执行）
     */
    private final Integer dispatchAttempt;

    private final Instant reportAt;

    public JobExpiredCmd(String jobId, Integer dispatchAttempt, Instant reportAt) {
        this.jobId = jobId;
        this.dispatchAttempt = dispatchAttempt;
        this.reportAt = reportAt;
    }
}