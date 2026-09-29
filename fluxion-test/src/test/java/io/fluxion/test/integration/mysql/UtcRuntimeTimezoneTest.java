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

package io.fluxion.test.integration.mysql;

import io.fluxion.server.infrastructure.dao.entity.JobEntity;
import io.fluxion.server.infrastructure.dao.repository.JobEntityRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UTC 运行态的跨时区验证（CONTEXT.md「统一时间模型」）：
 * JVM 默认时区不得影响 Instant 的落库/回读/条件更新语义——
 * hibernate.jdbc.time_zone=UTC 全权解释 DATETIME(3)，与机器部署时区无关（NTP 校时前提）。
 * <p>
 * 进程内把默认时区切到非 UTC（Asia/Shanghai / America/New_York）后执行同一断言集，
 * 期望与 UTC 默认时区下的行为逐位一致。**注意**：native 读回的 Timestamp 是绝对时刻，
 * 其 toLocalDateTime 会按 JVM default 展开；字面量断言须用 SQL CAST。
 */
@SpringBootTest(classes = MySqlTestApplication.class)
@ActiveProfiles("test-mysql")
@Transactional
@DisplayName("UTC runtime across JVM default timezones (H2)")
class UtcRuntimeTimezoneTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private JobEntityRepo jobEntityRepo;

    @Autowired
    private EntityManager entityManager;

    private TimeZone original;

    @AfterEach
    void tearDown() {
        if (original != null) {
            TimeZone.setDefault(original);
            original = null;
        }
    }

    @Test
    @DisplayName("T1: JVM 默认时区 Asia/Shanghai 下，Instant 落库回读与条件更新均不漂移")
    void roundTripAndConditionalUpdateIndependentOfJvmDefaultTimeZone() {
        original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));

        Instant deadline = Instant.parse("2026-09-28T12:00:00.500Z");

        seedJob("job-utc-sh", deadline);

        // 回读：绝对时刻逐位一致
        assertThat(jobEntityRepo.findById("job-utc-sh").orElseThrow().getDeadlineAt()).isEqualTo(deadline);

        // 条件更新（过期收敛）以绝对时刻比较：过去时刻命中
        int expiredPast = entityManager.createQuery(
                "update JobEntity set status = :expired where jobId = :jobId and deadlineAt <= :now")
            .setParameter("expired", "expired").setParameter("jobId", "job-utc-sh")
            .setParameter("now", deadline.plusMillis(1)).executeUpdate();
        assertThat(expiredPast).isEqualTo(1);

        // 未来截止不命中
        Instant futureDeadline = deadline.plusSeconds(3600);
        seedJob("job-utc-sh2", futureDeadline);
        int expiredFuture = entityManager.createQuery(
                "update JobEntity set status = :expired where jobId = :jobId and deadlineAt <= :now")
            .setParameter("expired", "expired").setParameter("jobId", "job-utc-sh2")
            .setParameter("now", deadline).executeUpdate();
        assertThat(expiredFuture).isEqualTo(0);
    }

    @Test
    @DisplayName("T2: 落库墙钟即 UTC：默认时区 America/New_York 下仍存 UTC 字面量")
    void storedWallClockShouldBeUtc() {
        original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));

        // 12:00:00.500Z：若误绑本机墙钟（纽约）会得 08:00；若上海墙钟则 20:00
        Instant deadline = Instant.parse("2026-09-28T12:00:00.500Z");

        seedJob("job-utc-ny", deadline);

        // 以 SQL CAST 直读存储字面量：必须等于 UTC 墙钟，证明 jdbc.time_zone=UTC 全权解释
        String literal = (String) entityManager.createNativeQuery(
                "select cast(deadline_at as varchar(64)) from fluxion_job where job_id = :id")
            .setParameter("id", "job-utc-ny").getSingleResult();
        assertThat(literal.trim()).startsWith("2026-09-28 12:00:00.5");

        // 而绝对时刻回读（经 Hibernate）亦无损
        assertThat(jobEntityRepo.findById("job-utc-ny").orElseThrow().getDeadlineAt()).isEqualTo(deadline);
    }

    private void seedJob(String jobId, Instant deadlineAt) {
        JobEntity job = new JobEntity();
        job.setJobId(jobId);
        job.setExecutionId("execution-utc");
        job.setBucket(1);
        job.setJobType("executor");
        job.setRefId("node-utc");
        job.setStatus("inited");
        job.setTriggerAt(Instant.now());
        job.setDeadlineAt(deadlineAt);
        job.setRetryTimes(0);
        jobEntityRepo.saveAndFlush(job);
    }
}