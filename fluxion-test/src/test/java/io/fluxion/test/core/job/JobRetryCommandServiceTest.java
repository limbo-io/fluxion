package io.fluxion.test.core.job;

import io.fluxion.remote.core.constants.JobStatus;
import io.fluxion.server.core.job.Job;
import io.fluxion.server.core.job.JobType;
import io.fluxion.server.core.job.cmd.JobRetryCmd;
import io.fluxion.server.core.job.service.JobCommandService;
import io.fluxion.server.infrastructure.dao.entity.JobEntity;
import io.fluxion.server.infrastructure.dao.repository.JobEntityRepo;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobRetryCommandServiceTest {

    @Test
    void shouldResetAndRerunPersistedJob() throws Exception {
        JobEntity entity = new JobEntity();
        entity.setJobId("job-1");
        entity.setExecutionId("execution-1");
        entity.setRefId("task-1");
        entity.setJobType(JobType.EXECUTOR.value);
        entity.setTriggerAt(LocalDateTime.now());
        JobEntityRepo jobRepo = mock(JobEntityRepo.class);
        when(jobRepo.findById("job-1")).thenReturn(Optional.of(entity));
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(1);
        CapturingJobCommandService service = new CapturingJobCommandService();
        setField(service, "jobEntityRepo", jobRepo);
        setField(service, "entityManager", entityManager);

        assertTrue(service.handle(new JobRetryCmd("job-1", 2)));
        assertEquals("job-1", service.rerunJob.getJobId());
        assertEquals(JobType.EXECUTOR, service.rerunJob.getType());
        assertEquals(JobStatus.INITED, service.rerunJob.getStatus());
        assertEquals(2, service.rerunJob.getRetryTimes());
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = JobCommandService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class CapturingJobCommandService extends JobCommandService {

        private Job rerunJob;

        @Override
        protected void run(Job job) {
            rerunJob = job;
        }
    }
}
