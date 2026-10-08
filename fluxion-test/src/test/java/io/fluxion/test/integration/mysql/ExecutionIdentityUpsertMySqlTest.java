/*
 * Copyright 2025-2030 fluxion-io Team (https://github.com/fluxion-io).
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

import io.fluxion.server.core.execution.Executable;
import io.fluxion.server.core.execution.Execution;
import io.fluxion.server.core.execution.ExecutionStatus;
import io.fluxion.server.core.execution.cmd.ExecutionCreateCmd;
import io.fluxion.server.core.execution.service.ExecutionCommandService;
import io.fluxion.server.core.trigger.TriggerType;
import io.fluxion.server.infrastructure.dao.entity.ExecutionEntity;
import io.fluxion.server.infrastructure.dao.repository.ExecutionEntityRepo;
import io.fluxion.server.infrastructure.dao.tx.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 MySQL 下的 Execution 身份幂等写入验证（计划：真实 MySQL 调度并发验证 阶段 2）。
 * <p>
 * H2 已覆盖业务与事务语义；本类只验证 H2 无法替代的 MySQL 方言行为：
 * <ul>
 *   <li>{@code ExecutionEntityRepo.insertExecutionIdentity} 的
 *       {@code INSERT ... ON DUPLICATE KEY UPDATE} no-op 在真实 InnoDB + REPEATABLE READ
 *       下的受影响行数与谓词行为；</li>
 *   <li>两个独立事务同时为相同 {@code (trigger_id, trigger_at)} 创建 Execution：一个 CREATED、
 *       一个 EXISTING，同点竞争不抛唯一键异常、不致任一事务 rollback-only——后到者在唯一索引
 *       上等待先行事务提交后按 no-op 收敛（默认隔离级别的锁等待/提交顺序）；</li>
 *   <li>COURIERS/J 行为差异（useAffectedRows / CLIENT_FOUND_ROWS）不得影响 CREATED/EXISTING
 *       判定——生产实现以回查行的 executionId 归属为准，不读行数。</li>
 * </ul>
 * 记录项（版本/隔离级别）由 testMetadataVersionAndIsolation 固化到断言输出。
 */
@SpringBootTest(classes = MySqlTestApplication.class)
@ActiveProfiles("test-real-mysql")
@DisplayName("Execution identity idempotent upsert on real MySQL")
class ExecutionIdentityUpsertMySqlTest extends AbstractMySqlIntegrationTest {

    /** 计划要求：并发竞争至少重复 20 次无失败 */
    private static final int CONCURRENT_ROUNDS = 20;

    @Autowired
    private ExecutionCommandService executionCommandService;

    @Autowired
    private ExecutionEntityRepo executionEntityRepo;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionService transactionService;

    @Test
    @DisplayName("环境记录：MySQL 版本、InnoDB 隔离级别、会话时区 UTC 与连接 affected-rows 语义")
    void testMetadataVersionAndIsolation() {
        Object version = entityManager
            .createNativeQuery("SELECT VERSION()").getSingleResult();
        Object isolation = entityManager
            .createNativeQuery("SELECT @@transaction_isolation").getSingleResult();
        // InnoDB 默认 REPEATABLE READ；upheld 记录到测试输出（审计 progress 的数据源）
        assertThat(String.valueOf(isolation)).isEqualToIgnoringCase("REPEATABLE-READ");
        assertThat(String.valueOf(version)).startsWith("8.");

        // 统一时间模型：锁 SQL 的 NOW(3) 按服务器会话时区求值；expire_at 按 Hibernate
        // jdbc.time_zone=UTC 落 DATETIME。两者必须同口径，否则过期判定偏移一个时差。
        // 会话时区由 connection-init-sql 固定为 '+00:00'（断言覆盖两种合法返回值）。
        Object sessionTz = entityManager
            .createNativeQuery("SELECT @@session.time_zone").getSingleResult();
        assertThat(String.valueOf(sessionTz)).isIn("+00:00", "UTC");
        // 双重佐证：锁谓词使用会话时钟 NOW(3)，必须与 UTC 时钟函数一致（时间差为零）。
        // CAST AS CHAR 避免 TIME 类型被驱动转成 java.sql.Time 后按 JVM 时区渲染的假偏移。
        Object skew = entityManager
            .createNativeQuery("SELECT CAST(TIMEDIFF(NOW(3), UTC_TIMESTAMP(3)) AS CHAR)")
            .getSingleResult();
        assertThat(String.valueOf(skew)).startsWith("00:00:00");

        System.out.println("[real-mysql] version=" + version + " isolation=" + isolation + " sessionTz=" + sessionTz + " clockSkew=" + skew);
    }

    @Test
    @DisplayName("谓词与行数语义：新插入 affected=1；同身份未过期重复 affected=0；恰一行落库")
    void testUpsertPredicateAndRowcountOnFreshRow() {
        String triggerId = "trigger-upsert-" + UUID.randomUUID();
        Instant triggerAt = Instant.now().plusSeconds(300);
        Instant deadlineAt = triggerAt.plusSeconds(60);
        int bucket = 7;

        // 新行插入：不受 CLIENT_FOUND_ROWS 影响，恒 1
        Integer inserted = transactionService.transactional(() -> executionEntityRepo.insertExecutionIdentity(
            "execution-upsert-a-" + UUID.randomUUID(), triggerId,
            TriggerType.SCHEDULE.value, triggerId, "executor", "v1",
            ExecutionStatus.PENDING.value, triggerAt, deadlineAt, bucket));
        assertThat(inserted).isEqualTo(1);

        // 同身份重复：拟插入 trigger_at 与既有行相等 → no-op；affected=0（未插入）
        Integer duplicated = transactionService.transactional(() -> executionEntityRepo.insertExecutionIdentity(
            "execution-upsert-b-" + UUID.randomUUID(), triggerId,
            TriggerType.SCHEDULE.value, triggerId, "executor", "v1",
            ExecutionStatus.PENDING.value, triggerAt, deadlineAt, bucket));
        assertThat(duplicated).isZero();

        // 恰一行；字段快照完整正确（拟插入值未被 no-op 覆写）
        ExecutionEntity row = executionEntityRepo.findByTriggerIdAndTriggerAt(triggerId, triggerAt);
        assertThat(row).isNotNull();
        assertThat(row.getExecutionId()).startsWith("execution-upsert-a-");
        assertThat(row.getTriggerId()).isEqualTo(triggerId);
        assertThat(row.getTriggerAt()).isEqualTo(triggerAt);
        assertThat(row.getStatus()).isEqualTo(ExecutionStatus.PENDING.value);
        assertThat(row.getExecutableVersion()).isEqualTo("v1");
        assertThat(row.getExecutableType()).isEqualTo("executor");
        assertThat(row.getExecutableId()).isEqualTo(triggerId);
        assertThat(row.getDeadlineAt()).isEqualTo(deadlineAt);
        assertThat(row.getBucket()).isEqualTo(bucket);
        assertThat(row.getFireAttempt()).isZero();
        assertThat(row.getCreatedAt()).isNotNull();
        assertThat(row.getUpdatedAt()).isNotNull();

        long count = executionEntityRepo.findAll().stream()
            .filter(e -> triggerId.equals(e.getTriggerId())).count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("两独立事务并发创建同点：恰一个 CREATED 一个 EXISTING，双方提交、无唯一键异常")
    void testConcurrentSamePointCreateOneWinner() throws Exception {
        for (int round = 0; round < CONCURRENT_ROUNDS; round++) {
            String triggerId = "trigger-race-" + UUID.randomUUID();
            Instant triggerAt = Instant.now().plusSeconds(300);

            CyclicBarrier barrier = new CyclicBarrier(2);
            List<ExecutionCreateCmd.Response> responses = new CopyOnWriteArrayList<>();
            List<Throwable> failures = new CopyOnWriteArrayList<>();
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch done = new CountDownLatch(2);
            try {
                for (int i = 0; i < 2; i++) {
                    pool.submit(() -> {
                        try {
                            barrier.await(10, TimeUnit.SECONDS);
                            // 同点竞争双方先后触达：先行者插入，后行者在唯一索引上等待其提交
                            ExecutionCreateCmd.Response response = executionCommandService.handle(
                                new ExecutionCreateCmd(
                                    triggerId, TriggerType.SCHEDULE, stubExecutable(triggerId), triggerAt));
                            responses.add(response);
                        } catch (Throwable t) {
                            // 唯一键异常逸出或事务 rollback-only 都是失败
                            failures.add(t);
                        } finally {
                            done.countDown();
                        }
                        return null;
                    });
                }
                assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
            } finally {
                pool.shutdownNow();
            }

            // 同点竞争不得产生任何异常；两事务都成功
            assertThat(failures).as("round %s failures", round).isEmpty();
            assertThat(responses).as("round %s", round).hasSize(2);
            // 恰一个 CREATED 一个 EXISTING
            assertThat(responses.stream().filter(ExecutionCreateCmd.Response::isCreated).count())
                .as("round %s created count", round).isEqualTo(1);
            // 两个响应指向同一行（EXISTING 方拿回的是先行者的实例）
            String winnerId = responses.stream()
                .filter(ExecutionCreateCmd.Response::isCreated)
                .map(r -> r.getExecution().getId()).findFirst().orElseThrow();
            for (ExecutionCreateCmd.Response r : responses) {
                assertThat(r.getExecution().getId()).isEqualTo(winnerId);
            }
            // 恰一行；快照字段正确
            ExecutionEntity row = executionEntityRepo.findByTriggerIdAndTriggerAt(triggerId, triggerAt);
            assertThat(row).isNotNull();
            assertThat(row.getExecutionId()).isEqualTo(winnerId);
            assertThat(row.getStatus()).isEqualTo(ExecutionStatus.PENDING.value);
            long count = executionEntityRepo.findAll().stream()
                .filter(e -> triggerId.equals(e.getTriggerId())).count();
            assertThat(count).as("round %s rows", round).isEqualTo(1);
        }
    }

    private Executable stubExecutable(String id) {
        return new Executable() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String version() {
                return "v1";
            }

            @Override
            public io.fluxion.server.core.execution.ExecutableType type() {
                return io.fluxion.server.core.execution.ExecutableType.EXECUTOR;
            }

            @Override
            public void execute(Execution execution) {
                // 身份创建链路不触发执行
            }

            @Override
            public boolean success(String executionId, String refId, Instant time) {
                return false;
            }

            @Override
            public boolean fail(String executionId, String refId, Instant time) {
                return false;
            }

            @Override
            public io.fluxion.server.core.job.Job.Config config(String refId) {
                return null;
            }
        };
    }
}