package io.fluxion.test.integration.database;

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
import io.fluxion.server.core.worker.Worker;
import io.fluxion.server.core.worker.cmd.WorkerSliceOfflineCmd;
import io.fluxion.server.core.worker.service.WorkerCommandService;
import io.fluxion.server.infrastructure.dao.entity.BucketEntity;
import io.fluxion.server.infrastructure.dao.entity.JobEntity;
import io.fluxion.server.infrastructure.dao.entity.WorkerEntity;
import io.fluxion.server.infrastructure.dao.entity.WorkerMetricEntity;
import io.fluxion.server.infrastructure.dao.repository.BucketEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.JobEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.WorkerEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.WorkerMetricEntityRepo;
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
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import io.limbo.cqrs.core.queryhandling.Query;

@SpringBootTest(classes = MySqlTestApplication.class)
@ActiveProfiles("test-mysql")
@Transactional
class JobLeaseServiceTest extends AbstractMySqlIntegrationTest {

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

    @Autowired
    private WorkerEntityRepo workerEntityRepo;

    @Autowired
    private WorkerMetricEntityRepo workerMetricEntityRepo;

    @Autowired
    private WorkerCommandService workerCommandService;

    @AfterEach
    void clearJobs() {
        jobEntityRepo.deleteAll();
        bucketEntityRepo.deleteAll();
        workerMetricEntityRepo.deleteAll();
        workerEntityRepo.deleteAll();
    }

    @Test
    void shouldAssignMonotonicAttemptsOnlyToTheLeaseOwner() {
        jobEntityRepo.saveAndFlush(newJob("job-lease"));

        Integer firstAttempt = jobLeaseService.claimFirstAttempt("job-lease", "broker-a", Instant.now().plusSeconds(15));
        Integer deniedAttempt = jobLeaseService.claimFirstAttempt("job-lease", "broker-b", Instant.now().plusSeconds(15));
        Integer secondAttempt = jobLeaseService.nextAttempt("job-lease", "broker-a", firstAttempt, Instant.now().plusSeconds(15));
        boolean renewed = jobLeaseService.renew("job-lease", "broker-a", secondAttempt, Instant.now().plusSeconds(15));
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
        job.setLeaseUntil(Instant.now().plusSeconds(2));
        jobEntityRepo.saveAndFlush(job);

        JobEntity deletedJob = newJob("job-lease-renew-deleted");
        deletedJob.setLeaseOwner(brokerId);
        deletedJob.setDispatchAttempt(1);
        deletedJob.setLeaseUntil(Instant.now().plusSeconds(2));
        deletedJob.setDeleted(true);
        jobEntityRepo.saveAndFlush(deletedJob);

        new JobLeaseRenewChecker().run();
        entityManager.clear();

        assertThat(jobEntityRepo.findById(job.getJobId()).orElseThrow().getLeaseUntil())
            .isAfter(Instant.now().plusSeconds(10));
        assertThat(jobEntityRepo.findById(deletedJob.getJobId()).orElseThrow().getLeaseUntil())
            .isBefore(Instant.now().plusSeconds(5));
    }

    @Test
    void shouldRejectReportFromAnOlderDispatchAttempt() {
        JobEntity job = newJob("job-result-fence");
        job.setStatus(JobStatus.RUNNING.value);
        job.setDispatchAttempt(2);
        jobEntityRepo.saveAndFlush(job);

        JobReportCmd.Response oldAttempt = jobCommandService.handle(new JobReportCmd(
            job.getJobId(), 1, new BaseNode(Protocol.HTTP, "worker-a", 8080), Instant.now(), null, JobStatus.RUNNING
        ));
        JobReportCmd.Response currentAttempt = jobCommandService.handle(new JobReportCmd(
            job.getJobId(), 2, new BaseNode(Protocol.HTTP, "worker-a", 8080), Instant.now(), null, JobStatus.RUNNING
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
            job.getJobId(), Instant.now(), 1, "stale dispatch failure", null
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
        Instant retryAt = Instant.now().minusSeconds(1);

        assertThat(jobRetryService.schedule(job.getJobId(), 0, 1, retryAt)).isTrue();
        assertThat(jobRetryService.activate(job.getJobId(), 1, Instant.now())).isTrue();

        entityManager.clear();
        assertThat(jobEntityRepo.findById(job.getJobId()).orElseThrow().getStatus()).isEqualTo(JobStatus.INITED.value);
    }

    @Test
    void shouldCreateOnlyOneRetryWaitForRepeatedFailureHandling() {
        JobEntity job = newJob("job-single-retry");
        job.setStatus(JobStatus.FAILED.value);
        job.setRetryTimes(0);
        jobEntityRepo.saveAndFlush(job);

        Instant retryAt = Instant.now().plusSeconds(10);
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
        due.setTimeoutAt(Instant.now().minusSeconds(1));
        due.setLeaseUntil(Instant.now().minusSeconds(1));
        jobEntityRepo.saveAndFlush(due);

        JobEntity unrelated = newJob("job-unrelated");
        unrelated.setStatus(JobStatus.RUNNING.value);
        unrelated.setWorkerAddress("http://worker-b:8080");
        unrelated.setDispatchAttempt(4);
        unrelated.setTimeoutAt(Instant.now().plusSeconds(60));
        unrelated.setLeaseUntil(Instant.now().plusSeconds(60));
        jobEntityRepo.saveAndFlush(unrelated);

        assertThat(Query.query(new JobRunningByWorkerQuery("http://worker-a:8080", null, 10)).getJobs())
            .extracting(JobRunningByWorkerQuery.JobRunning::getJobId,
                JobRunningByWorkerQuery.JobRunning::getDispatchAttempt)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("job-due", 3));
        assertThat(Query.query(new JobTimeoutDueQuery(10, Instant.now())).getJobs())
            .extracting(JobTimeoutDueQuery.JobTimeout::getJobId,
                JobTimeoutDueQuery.JobTimeout::getDispatchAttempt)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("job-due", 3));
        assertThat(Query.query(new JobExpiredLeaseQuery(10, Instant.now())).getJobs())
            .extracting(JobExpiredLeaseQuery.JobLease::getJobId,
                JobExpiredLeaseQuery.JobLease::getDispatchAttempt)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("job-due", 3));
    }

    @Test
    void shouldPageOfflineWorkersByHeartbeatWithoutSkippingAny() {
        Instant cutoff = Instant.now().minusSeconds(10);
        saveWorker("worker-a", cutoff.minusSeconds(3));
        saveWorker("worker-b", cutoff.minusSeconds(2));
        saveWorker("worker-c", cutoff.minusSeconds(1));
        saveWorker("worker-fresh", cutoff.plusSeconds(1));

        WorkerSliceOfflineCmd.Response first = workerCommandService.handle(
            new WorkerSliceOfflineCmd(cutoff, null, null, 2)
        );
        WorkerSliceOfflineCmd.Response second = workerCommandService.handle(
            new WorkerSliceOfflineCmd(cutoff, first.getNextCursorHeartbeatAt(), first.getNextCursorWorkerId(), 2)
        );
        entityManager.clear();

        assertThat(first.getWorkerIds()).containsExactly("worker-a", "worker-b");
        assertThat(second.getWorkerIds()).containsExactly("worker-c");
        assertThat(workerEntityRepo.findById("worker-a").orElseThrow().getStatus()).isEqualTo(Worker.Status.OFFLINE.status);
        assertThat(workerEntityRepo.findById("worker-b").orElseThrow().getStatus()).isEqualTo(Worker.Status.OFFLINE.status);
        assertThat(workerEntityRepo.findById("worker-c").orElseThrow().getStatus()).isEqualTo(Worker.Status.OFFLINE.status);
        assertThat(workerEntityRepo.findById("worker-fresh").orElseThrow().getStatus()).isEqualTo(Worker.Status.ONLINE.status);
    }

    private void saveWorker(String workerId, Instant lastHeartbeatAt) {
        WorkerEntity worker = new WorkerEntity();
        worker.setWorkerId(workerId);
        worker.setAppId("test-app");
        worker.setProtocol(Protocol.HTTP.value);
        worker.setHost("127.0.0.1");
        worker.setPort(8080);
        worker.setStatus(Worker.Status.ONLINE.status);
        worker.setEnabled(true);
        workerEntityRepo.save(worker);

        WorkerMetricEntity metric = new WorkerMetricEntity();
        metric.setWorkerId(workerId);
        metric.setLastHeartbeatAt(lastHeartbeatAt);
        workerMetricEntityRepo.save(metric);
        workerMetricEntityRepo.flush();
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
        job.setTriggerAt(Instant.now());
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        job.setDeleted(false);
        return job;
    }
}
