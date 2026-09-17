/*
 * Copyright 2025-2030 fluxion-io Team (https://github.com/Fluxion-io).
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

package io.fluxion.test.integration.mysql;

import io.fluxion.server.core.observation.ObservationOverviewQuery;
import io.fluxion.server.infrastructure.dao.entity.ExecutionEntity;
import io.fluxion.server.infrastructure.dao.entity.JobEntity;
import io.fluxion.server.infrastructure.dao.entity.WorkerEntity;
import io.fluxion.server.infrastructure.dao.repository.ExecutionEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.JobEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.WorkerEntityRepo;
import io.limbo.cqrs.core.queryhandling.Query;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = MySqlTestApplication.class)
@ActiveProfiles("test-mysql")
@Transactional
@DisplayName("Observation overview stock snapshot tests")
class ObservationOverviewMySqlTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private ExecutionEntityRepo executionEntityRepo;

    @Autowired
    private JobEntityRepo jobEntityRepo;

    @Autowired
    private WorkerEntityRepo workerEntityRepo;

    @Test
    @DisplayName("状态分布 zero-fill 全枚举键且只计数未删除记录")
    void shouldReturnZeroFilledDistributionsCountingOnlyLiveRows() {
        executionEntityRepo.saveAndFlush(pendingExecution(LocalDateTime.now().minusMinutes(1)));
        ExecutionEntity deleted = pendingExecution(LocalDateTime.now());
        deleted.setDeleted(true);
        executionEntityRepo.saveAndFlush(deleted);

        jobEntityRepo.saveAndFlush(job("retry_wait"));
        WorkerEntity online = worker("online");
        WorkerEntity offline = worker("offline");
        workerEntityRepo.saveAndFlush(online);
        workerEntityRepo.saveAndFlush(offline);

        ObservationOverviewQuery.Response response = Query.query(new ObservationOverviewQuery());

        Map<String, Long> executions = response.getExecutions();
        // zero-fill：全部已知 Execution 状态键均存在
        for (io.fluxion.server.core.execution.ExecutionStatus status
            : io.fluxion.server.core.execution.ExecutionStatus.values()) {
            if (status == io.fluxion.server.core.execution.ExecutionStatus.UNKNOWN) {
                continue;
            }
            assertThat(executions).containsKey(status.value);
        }
        // 只有 1 条未删除 pending；已删除行不计入
        assertThat(executions.get("pending")).isEqualTo(1L);

        Map<String, Long> jobs = response.getJobs();
        for (io.fluxion.remote.core.constants.JobStatus status : io.fluxion.remote.core.constants.JobStatus.values()) {
            if (status == io.fluxion.remote.core.constants.JobStatus.UNKNOWN) {
                continue;
            }
            assertThat(jobs).containsKey(status.value);
        }
        assertThat(jobs.get("retry_wait")).isEqualTo(1L);

        assertThat(response.getWorkers()).containsEntry("online", 1L).containsEntry("offline", 1L);
    }

    @Test
    @DisplayName("积压/misfire 候选/可回收租约的边界判定")
    void shouldCountBacklogMisfireCandidatesAndReclaimableClaims() {
        // 积压：pending 到期（含）计数
        executionEntityRepo.saveAndFlush(pendingExecution(LocalDateTime.now().minusSeconds(10)));
        executionEntityRepo.saveAndFlush(pendingExecution(LocalDateTime.now().plusMinutes(5)));

        // misfire 候选：now - triggerAt > 5s
        executionEntityRepo.saveAndFlush(pendingExecution(LocalDateTime.now().minusSeconds(7)));
        executionEntityRepo.saveAndFlush(pendingExecution(LocalDateTime.now().minusSeconds(3)));

        // 可回收租约：claimed 且 leaseUntil 过期
        ExecutionEntity expiredClaim = pendingExecution(LocalDateTime.now());
        expiredClaim.setStatus("claimed");
        expiredClaim.setLeaseUntil(LocalDateTime.now().minusSeconds(1));
        executionEntityRepo.saveAndFlush(expiredClaim);
        ExecutionEntity liveClaim = pendingExecution(LocalDateTime.now());
        liveClaim.setStatus("claimed");
        liveClaim.setLeaseUntil(LocalDateTime.now().plusSeconds(15));
        executionEntityRepo.saveAndFlush(liveClaim);

        ObservationOverviewQuery.Response response = Query.query(new ObservationOverviewQuery());

        // 4 条 pending 已到期（10s 前、7s 前、3s 前、以及被转成 claimed 的两条原 triggerAt 也已到期）
        assertThat(response.getExecutionBacklog()).isEqualTo(3L);
        // 10s 前、7s 前 > 5s 阈值；3s 前不算
        assertThat(response.getExecutionMisfireCandidates()).isEqualTo(2L);
        assertThat(response.getExecutionReclaimableClaims()).isEqualTo(1L);
    }

    private ExecutionEntity pendingExecution(LocalDateTime triggerAt) {
        ExecutionEntity entity = new ExecutionEntity();
        entity.setExecutionId("execution-" + UUID.randomUUID());
        entity.setTriggerId("trigger-" + UUID.randomUUID());
        entity.setTriggerType("schedule");
        entity.setExecutableId("executable-1");
        entity.setExecutableType("executor");
        entity.setExecutableVersion("1");
        entity.setStatus("pending");
        entity.setTriggerAt(triggerAt);
        entity.setBucket(1);
        entity.setFireAttempt(0);
        return entity;
    }

    private JobEntity job(String status) {
        JobEntity entity = new JobEntity();
        entity.setJobId("job-" + UUID.randomUUID());
        entity.setExecutionId("execution-" + UUID.randomUUID());
        entity.setBucket(1);
        entity.setJobType("normal");
        entity.setRefId("ref-1");
        entity.setStatus(status);
        entity.setTriggerAt(LocalDateTime.now());
        entity.setWorkerAddress("127.0.0.1:8080");
        entity.setRetryTimes(0);
        entity.setDispatchAttempt(0);
        return entity;
    }

    private WorkerEntity worker(String status) {
        WorkerEntity entity = new WorkerEntity();
        entity.setWorkerId("worker-" + UUID.randomUUID());
        entity.setAppId("app-1");
        entity.setProtocol("http");
        entity.setHost("127.0.0.1");
        entity.setPort(8080);
        entity.setStatus(status);
        entity.setEnabled(true);
        return entity;
    }
}