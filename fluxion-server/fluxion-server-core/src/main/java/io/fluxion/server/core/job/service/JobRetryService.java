package io.fluxion.server.core.job.service;

import io.fluxion.remote.core.constants.JobStatus;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import java.time.LocalDateTime;

/**
 * 持久化 Job 重试等待状态；内存计时器可在 Broker 重启后由扫描器重建。
 */
@Service
public class JobRetryService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public boolean schedule(String jobId, int previousRetryTimes, int retryTimes, LocalDateTime nextRetryAt) {
        return entityManager.createQuery("update JobEntity set status = :retryWait, retryTimes = :retryTimes, "
                + "nextRetryAt = :nextRetryAt, leaseOwner = null, leaseUntil = null "
                + "where jobId = :jobId and status = :failed and retryTimes = :previousRetryTimes")
            .setParameter("retryWait", JobStatus.RETRY_WAIT.value)
            .setParameter("retryTimes", retryTimes)
            .setParameter("nextRetryAt", nextRetryAt)
            .setParameter("jobId", jobId)
            .setParameter("failed", JobStatus.FAILED.value)
            .setParameter("previousRetryTimes", previousRetryTimes)
            .executeUpdate() == 1;
    }

    @Transactional
    public boolean activate(String jobId, int retryTimes, LocalDateTime now) {
        return entityManager.createQuery("update JobEntity set status = :inited, nextRetryAt = null "
                + "where jobId = :jobId and status = :retryWait and retryTimes = :retryTimes "
                + "and nextRetryAt <= :now")
            .setParameter("inited", JobStatus.INITED.value)
            .setParameter("retryWait", JobStatus.RETRY_WAIT.value)
            .setParameter("jobId", jobId)
            .setParameter("retryTimes", retryTimes)
            .setParameter("now", now)
            .executeUpdate() == 1;
    }
}
