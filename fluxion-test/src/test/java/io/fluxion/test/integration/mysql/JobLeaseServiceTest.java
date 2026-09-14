package io.fluxion.test.integration.mysql;

import io.fluxion.server.core.job.service.JobLeaseService;
import io.fluxion.server.core.job.service.JobRetryService;
import io.fluxion.server.core.job.cmd.JobFailCmd;
import io.fluxion.server.core.job.cmd.JobReportCmd;
import io.fluxion.server.core.job.query.JobExpiredLeaseQuery;
import io.fluxion.server.core.job.query.JobRunningByWorkerQuery;
import io.fluxion.server.core.job.query.JobTimeoutDueQuery;
import io.fluxion.server.core.job.service.JobCommandService;
import io.fluxion.server.core.broker.task.JobLeaseRenewChecker;
import io.fluxion.server.core.broker.Broker;
import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.infrastructure.dao.entity.BucketEntity;
import io.fluxion.server.infrastructure.dao.entity.JobEntity;
import io.fluxion.server.infrastructure.dao.repository.BucketEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.JobEntityRepo;
import io.fluxion.remote.core.cluster.BaseNode;
import io.fluxion.remote.core.constants.JobStatus;
import io.fluxion.remote.core.constants.Protocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.transaction.Transactional;
import javax.persistence.EntityManager;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.limbo.cqrs.spring.gateway.QueryGateway;
@SpringBootTest(classes = MySqlTestApplication.class)
@ActiveProfiles("test-mysql")
@Transactional
class JobLeaseServiceTest extends AbstractMySqlIntegrationTest {
    @Autowired
    private QueryGateway queryGateway;

    @Autowired
    private JobEntityRepo jobEntityRepo;

    @Autowired
    private BucketEntityRepo bucketEntityRepo;

    @Autowired
    private JobLeaseService jobLeaseService;

    @Autowired
    private JobCommandService jobCommandService;

    @Autowired
    private JobRetryService jobRetryService;

    @Autowired
    private EntityManager entityManager;

    @AfterEach
    void clearJobs() {
        jobEntityRepo.deleteAll();
        bucketEntityRepo.deleteAll();
    }

    @Test
    void shouldAssignMonotonicAttemptsOnlyToTheLeaseOwner() {
        jobEntityRepo.saveAndFlush(newJob("job-lease"));

        Integer firstAttempt = jobLeaseService.claimFirstAttempt("job-lease", "broker-a", LocalDateTime.now().plusSeconds(15));
        Integer deniedAttempt = jobLeaseService.claimFirstAttempt("job-lease", "broker-b", LocalDateTime.now().plusSeconds(15));
        Integer secondAttempt = jobLeaseService.nextAttempt("job-lease", "broker-a", firstAttempt, LocalDateTime.now().plusSeconds(15));
        boolean renewed = jobLeaseService.renew("job-lease", "broker-a", secondAttempt, LocalDateTime.now().plusSeconds(15));
        boolean released = jobLeaseService.release("job-lease", "broker-a", secondAttempt);

        assertThat(firstAttempt).isEqualTo(1);
        assertThat(deniedAttempt).isNull();
        assertThat(secondAttempt).isEqualTo(2);
        assertThat(renewed).isTrue();
        assertThat(released).isTrue();
        assertThat(jobEntityRepo.findById("job-lease").orElseThrow().getDispatchAttempt()).isEqualTo(2);
    }

    @Test
    void shouldRenewTheLeaseOfAnActiveJobOwnedByTheCurrentBroker() {
        String brokerId = "broker-renew";
        Broker broker = mock(Broker.class);
        when(broker.id()).thenReturn(brokerId);
        BrokerContext.initialize(broker);

        JobEntity job = newJob("job-lease-renew");
        job.setLeaseOwner(brokerId);
        job.setDispatchAttempt(1);
        job.setLeaseUntil(LocalDateTime.now().plusSeconds(2));
        jobEntityRepo.saveAndFlush(job);

        JobEntity deletedJob = newJob("job-lease-renew-deleted");
        deletedJob.setLeaseOwner(brokerId);
        deletedJob.setDispatchAttempt(1);
        deletedJob.setLeaseUntil(LocalDateTime.now().plusSeconds(2));
        deletedJob.setDeleted(true);
        jobEntityRepo.saveAndFlush(deletedJob);

        new JobLeaseRenewChecker().run();
        entityManager.clear();

        assertThat(jobEntityRepo.findById(job.getJobId()).orElseThrow().getLeaseUntil())
            .isAfter(LocalDateTime.now().plusSeconds(10));
        assertThat(jobEntityRepo.findById(deletedJob.getJobId()).orElseThrow().getLeaseUntil())
            .isBefore(LocalDateTime.now().plusSeconds(5));
    }

    @Test
    void shouldRejectReportFromAnOlderDispatchAttempt() {
        JobEntity job = newJob("job-result-fence");
        job.setStatus(JobStatus.RUNNING.value);
        job.setDispatchAttempt(2);
        jobEntityRepo.saveAndFlush(job);

        JobReportCmd.Response oldAttempt = jobCommandService.handle(new JobReportCmd(
            job.getJobId(), 1, new BaseNode(Protocol.HTTP, "worker-a", 8080), LocalDateTime.now(), null, JobStatus.RUNNING
        ));
        JobReportCmd.Response currentAttempt = jobCommandService.handle(new JobReportCmd(
            job.getJobId(), 2, new BaseNode(Protocol.HTTP, "worker-a", 8080), LocalDateTime.now(), null, JobStatus.RUNNING
        ));

        assertThat(oldAttempt.isSuccess()).isFalse();
        assertThat(currentAttempt.isSuccess()).isTrue();
    }

    @Test
    void shouldRejectFailureFromAnOlderDispatchAttempt() {
        JobEntity job = newJob("job-failure-fence");
        job.setStatus(JobStatus.RUNNING.value);
        job.setDispatchAttempt(2);
        jobEntityRepo.saveAndFlush(job);

        boolean accepted = jobCommandService.handle(new JobFailCmd(
            job.getJobId(), LocalDateTime.now(), 1, "stale dispatch failure", null
        ));

        entityManager.clear();
        assertThat(accepted).isFalse();
        assertThat(jobEntityRepo.findById(job.getJobId()).orElseThrow().getStatus()).isEqualTo(JobStatus.RUNNING.value);
    }

    @Test
    void shouldPersistRetryWaitUntilItIsDue() {
        JobEntity job = newJob("job-retry-wait");
        job.setStatus(JobStatus.FAILED.value);
        job.setRetryTimes(0);
        jobEntityRepo.saveAndFlush(job);
        LocalDateTime retryAt = LocalDateTime.now().minusSeconds(1);

        assertThat(jobRetryService.schedule(job.getJobId(), 0, 1, retryAt)).isTrue();
        assertThat(jobRetryService.activate(job.getJobId(), 1, LocalDateTime.now())).isTrue();

        entityManager.clear();
        assertThat(jobEntityRepo.findById(job.getJobId()).orElseThrow().getStatus()).isEqualTo(JobStatus.INITED.value);
    }

    @Test
    void shouldCreateOnlyOneRetryWaitForRepeatedFailureHandling() {
        JobEntity job = newJob("job-single-retry");
        job.setStatus(JobStatus.FAILED.value);
        job.setRetryTimes(0);
        jobEntityRepo.saveAndFlush(job);

        LocalDateTime retryAt = LocalDateTime.now().plusSeconds(10);
        assertThat(jobRetryService.schedule(job.getJobId(), 0, 1, retryAt)).isTrue();
        assertThat(jobRetryService.schedule(job.getJobId(), 0, 1, retryAt)).isFalse();

        entityManager.clear();
        JobEntity persisted = jobEntityRepo.findById(job.getJobId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(JobStatus.RETRY_WAIT.value);
        assertThat(persisted.getRetryTimes()).isEqualTo(1);
        assertThat(persisted.getNextRetryAt()).isEqualTo(retryAt);
    }

    @Test
    void shouldReturnCurrentAttemptForWorkerTimeoutAndLeaseRecoveryScans() {
        String brokerId = "broker-query";
        BucketEntity bucket = new BucketEntity();
        bucket.setBucket(1);
        bucket.setBrokerId(brokerId);
        bucketEntityRepo.saveAndFlush(bucket);
        Broker broker = mock(Broker.class);
        when(broker.id()).thenReturn(brokerId);
        BrokerContext.initialize(broker);

        JobEntity due = newJob("job-due");
        due.setStatus(JobStatus.RUNNING.value);
        due.setWorkerAddress("http://worker-a:8080");
        due.setDispatchAttempt(3);
        due.setTimeoutAt(LocalDateTime.now().minusSeconds(1));
        due.setLeaseUntil(LocalDateTime.now().minusSeconds(1));
        jobEntityRepo.saveAndFlush(due);

        JobEntity unrelated = newJob("job-unrelated");
        unrelated.setStatus(JobStatus.RUNNING.value);
        unrelated.setWorkerAddress("http://worker-b:8080");
        unrelated.setDispatchAttempt(4);
        unrelated.setTimeoutAt(LocalDateTime.now().plusMinutes(1));
        unrelated.setLeaseUntil(LocalDateTime.now().plusMinutes(1));
        jobEntityRepo.saveAndFlush(unrelated);

        assertThat(queryGateway.query(new JobRunningByWorkerQuery("http://worker-a:8080", 10)).getJobs())
            .extracting(JobRunningByWorkerQuery.JobRunning::getJobId,
                JobRunningByWorkerQuery.JobRunning::getDispatchAttempt)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("job-due", 3));
        assertThat(queryGateway.query(new JobTimeoutDueQuery(10, LocalDateTime.now())).getJobs())
            .extracting(JobTimeoutDueQuery.JobTimeout::getJobId,
                JobTimeoutDueQuery.JobTimeout::getDispatchAttempt)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("job-due", 3));
        assertThat(queryGateway.query(new JobExpiredLeaseQuery(10, LocalDateTime.now())).getJobs())
            .extracting(JobExpiredLeaseQuery.JobLease::getJobId,
                JobExpiredLeaseQuery.JobLease::getDispatchAttempt)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("job-due", 3));
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
