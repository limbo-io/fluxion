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

package io.fluxion.test.integration.database;

import io.fluxion.server.core.job.cmd.JobExpiredCmd;
import io.fluxion.server.core.job.runner.InputOutputJobRunner;
import io.fluxion.server.core.job.service.JobCommandService;
import io.fluxion.server.core.job.Job;
import io.fluxion.server.core.job.JobType;
import io.fluxion.server.infrastructure.dao.entity.ExecutionEntity;
import io.fluxion.server.infrastructure.dao.entity.JobEntity;
import io.fluxion.server.infrastructure.dao.repository.ExecutionEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.JobEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.JobRecordEntityRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 首批 Job 因激活截止时间被拒后的终态收敛验证（H2 MySQL 兼容模式）。
 * <p>
 * 领域依据 CONTEXT.md：
 * <ul>
 *   <li>「Job 派发结果」——REJECTED_DEADLINE 必须收敛为 EXPIRED 而不重试；</li>
 *   <li>「Execution 激活截止时间」——首个 Job 与 Execution 统一以 EXPIRED 结束且不再重试；</li>
 *   <li>「创建 Job 的原子边界」/「两层租约」——过期更新匹配 Job 当前状态与 dispatch attempt，旧下发结果不得覆盖新尝试。</li>
 * </ul>
 */
@SpringBootTest(classes = MySqlTestApplication.class)
@ActiveProfiles("test-mysql")
@DisplayName("Job deadline expiration tests (H2 row-level)")
class JobDeadlineExpireTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private JobCommandService jobCommandService;

    @Autowired
    private InputOutputJobRunner inputOutputJobRunner;

    @Autowired
    private JobEntityRepo jobEntityRepo;

    @Autowired
    private ExecutionEntityRepo executionEntityRepo;

    @Autowired
    private JobRecordEntityRepo jobRecordEntityRepo;

    private final List<String> seededJobIds = new ArrayList<>();
    private final List<String> seededExecutionIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        // 共享 H2 库：清除本类 seed，保持零足迹
        jobRecordEntityRepo.deleteAllInBatch();
        if (!seededJobIds.isEmpty()) {
            jobEntityRepo.deleteAllInBatch();
        }
        if (!seededExecutionIds.isEmpty()) {
            executionEntityRepo.deleteAllInBatch();
        }
        seededJobIds.clear();
        seededExecutionIds.clear();
    }

    @Test
    @DisplayName("E1: Worker REJECTED_DEADLINE → Job 与 Execution 统一 EXPIRED，不进入重试")
    void shouldExpireJobAndExecutionWhenWorkerRejectedByDeadline() {
        String executionId = seedExecution("running");
        String jobId = seedJob(executionId, "executor", 2,
            Instant.now().minusSeconds(5), Instant.now().plusSeconds(5));
        // 预置残留的重试等待字段：EXPIRED 必须将其清除，证明未走 RetryOption
        JobEntity seeded = jobEntityRepo.findById(jobId).orElseThrow();
        seeded.setNextRetryAt(Instant.now().plusSeconds(30));
        jobEntityRepo.saveAndFlush(seeded);

        boolean accepted = jobCommandService.handle(new JobExpiredCmd(jobId, 2, Instant.now()));

        assertThat(accepted).isTrue();
        JobEntity job = jobEntityRepo.findById(jobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo("expired");
        // EXPIRED 是不可重试终态：不进入 RetryOption，重试相关字段不残留
        assertThat(job.getNextRetryAt()).isNull();
        assertThat(job.getLeaseOwner()).isNull();
        assertThat(job.getTimeoutAt()).isNull();
        assertThat(job.getErrorMsg()).containsIgnoringCase("deadline");

        ExecutionEntity execution = executionEntityRepo.findById(executionId).orElseThrow();
        assertThat(execution.getStatus()).isEqualTo("expired");
        assertThat(execution.getEndAt()).isNotNull();

        // 审计记录已落
        assertThat(jobRecordEntityRepo.findById(new io.fluxion.server.infrastructure.dao.entity.JobRecordEntity.ID(jobId, 0)))
            .isPresent();
    }

    @Test
    @DisplayName("E2: 围栏——过期的旧下发拒绝（attempt 不匹配）被丢弃，不误杀新尝试")
    void shouldIgnoreStaleDeadlineRejectionFromOldAttempt() {
        String executionId = seedExecution("running");
        String jobId = seedJob(executionId, "executor", 3, Instant.now().plusSeconds(30), null);

        // 旧 attempt=1 的迟到拒绝到达时，Job 已被新 attempt=3 接管
        boolean applied = jobCommandService.handle(new JobExpiredCmd(jobId, 1, Instant.now()));

        assertThat(applied).isFalse();
        assertThat(jobEntityRepo.findById(jobId).orElseThrow().getStatus()).isEqualTo("inited");
        assertThat(executionEntityRepo.findById(executionId).orElseThrow().getStatus()).isEqualTo("running");
    }

    @Test
    @DisplayName("E3: 幂等——同一 JobExpiredCmd 重复发送只有第一次生效")
    void shouldApplyExpirationOnlyOnce() {
        String executionId = seedExecution("running");
        String jobId = seedJob(executionId, "executor", 1, Instant.now().minusSeconds(5), null);

        assertThat(jobCommandService.handle(new JobExpiredCmd(jobId, 1, Instant.now()))).isTrue();
        assertThat(jobCommandService.handle(new JobExpiredCmd(jobId, 1, Instant.now()))).isFalse();

        assertThat(jobEntityRepo.findById(jobId).orElseThrow().getStatus()).isEqualTo("expired");
        assertThat(executionEntityRepo.findById(executionId).orElseThrow().getStatus()).isEqualTo("expired");
    }

    @Test
    @DisplayName("E4: Execution 已进入其它终态：Job 过期不再反向改写 Execution")
    void shouldNotResurrectAlreadyFinishedExecution() {
        String executionId = seedExecution("failed");
        String jobId = seedJob(executionId, "executor", 1, Instant.now().minusSeconds(5), null);

        // Job 围栏命中（inited + attempt 匹配），Execution 围栏不命中（已 failed）
        boolean executed = jobCommandService.handle(new JobExpiredCmd(jobId, 1, Instant.now()));

        assertThat(executed).isFalse();
        assertThat(jobEntityRepo.findById(jobId).orElseThrow().getStatus()).isEqualTo("expired");
        assertThat(executionEntityRepo.findById(executionId).orElseThrow().getStatus()).isEqualTo("failed");
    }

    @Test
    @DisplayName("E5: 工作流根节点本地终检——Deadline 已过的 INPUT_OUTPUT Job 启动前收敛为 EXPIRED")
    void shouldExpireWorkflowRootJobLocallyWhenDeadlinePassed() {
        String executionId = seedExecution("running");
        // Deadline 域固定 UTC 口径（与本地终检/下发 RPC 同基准）
        String jobId = seedJob(executionId, "input_output", 0,
            Instant.now().minusSeconds(1), null);

        Job job = new Job();
        job.setJobId(jobId);
        job.setExecutionId(executionId);
        job.setRefId("start-node-1");
        job.setType(JobType.INPUT_OUTPUT);
        job.setDeadlineAt(Instant.now().minusSeconds(1));

        inputOutputJobRunner.run(job);

        assertThat(jobEntityRepo.findById(jobId).orElseThrow().getStatus()).isEqualTo("expired");
        assertThat(executionEntityRepo.findById(executionId).orElseThrow().getStatus()).isEqualTo("expired");
    }

    // ==================== seeds =====================

    /** execution 行：可选 status，start/end 不设 */
    private String seedExecution(String status) {
        ExecutionEntity entity = new ExecutionEntity();
        String executionId = "execution-" + UUID.randomUUID();
        entity.setExecutionId(executionId);
        entity.setTriggerId("trigger-" + UUID.randomUUID());
        entity.setTriggerType("schedule");
        entity.setExecutableId("executable-1");
        entity.setExecutableType("executor");
        entity.setExecutableVersion("1");
        entity.setStatus(status);
        entity.setTriggerAt(Instant.now());
        entity.setBucket(1);
        entity.setFireAttempt(0);
        executionEntityRepo.saveAndFlush(entity);
        seededExecutionIds.add(executionId);
        return executionId;
    }

    /**
     * job 行。
     *
     * @param deadlineAt 可空；非空时表示首批 Job 的激活截止时间快照
     * @param leaseUntil 可空的持有租约
     */
    private String seedJob(String executionId, String jobType, int dispatchAttempt,
                           Instant deadlineAt, Instant leaseUntil) {
        String jobId = "job-" + UUID.randomUUID();
        JobEntity entity = new JobEntity();
        entity.setJobId(jobId);
        entity.setExecutionId(executionId);
        entity.setBucket(1);
        entity.setJobType(jobType);
        entity.setRefId("node-" + UUID.randomUUID());
        entity.setStatus("inited");
        entity.setTriggerAt(Instant.now());
        entity.setRetryTimes(0);
        entity.setDeadlineAt(deadlineAt);
        entity.setDispatchAttempt(dispatchAttempt);
        entity.setLeaseOwner(leaseUntil == null ? null : "broker-a");
        entity.setLeaseUntil(leaseUntil);
        entity.setTimeoutAt(leaseUntil);
        jobEntityRepo.saveAndFlush(entity);
        seededJobIds.add(jobId);
        return jobId;
    }
}