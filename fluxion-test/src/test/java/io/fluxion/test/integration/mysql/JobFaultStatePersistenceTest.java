package io.fluxion.test.integration.mysql;

import io.fluxion.server.infrastructure.dao.entity.JobEntity;
import io.fluxion.server.infrastructure.dao.repository.JobEntityRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = MySqlTestApplication.class)
@ActiveProfiles("test-mysql")
@Transactional
class JobFaultStatePersistenceTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private JobEntityRepo jobEntityRepo;

    @Autowired
    private EntityManager entityManager;

    @AfterEach
    void clearJobs() {
        jobEntityRepo.deleteAll();
    }

    @Test
    void shouldPersistJobFaultStateColumns() {
        JobEntity job = newJob("job-fault-state");
        jobEntityRepo.saveAndFlush(job);

        entityManager.createNativeQuery("UPDATE fluxion_job SET dispatch_attempt = 3, lease_owner = 'broker-a', "
                + "lease_until = CURRENT_TIMESTAMP, timeout_at = CURRENT_TIMESTAMP, next_retry_at = CURRENT_TIMESTAMP "
                + "WHERE job_id = :jobId")
            .setParameter("jobId", job.getJobId())
            .executeUpdate();
        entityManager.flush();

        Object[] row = (Object[]) entityManager.createNativeQuery("SELECT dispatch_attempt, lease_owner, lease_until, timeout_at, next_retry_at "
                + "FROM fluxion_job WHERE job_id = :jobId")
            .setParameter("jobId", job.getJobId())
            .getSingleResult();
        assertThat(((Number) row[0]).intValue()).isEqualTo(3);
        assertThat(row[1]).isEqualTo("broker-a");
        assertThat(row[2]).isNotNull();
        assertThat(row[3]).isNotNull();
        assertThat(row[4]).isNotNull();
    }

    private JobEntity newJob(String jobId) {
        JobEntity job = new JobEntity();
        job.setJobId(jobId);
        job.setExecutionId("execution-1");
        job.setBucket(1);
        job.setJobType("executor");
        job.setRefId(jobId);
        job.setStatus("inited");
        job.setRetryTimes(0);
        job.setTriggerAt(LocalDateTime.now());
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        job.setDeleted(false);
        return job;
    }
}
