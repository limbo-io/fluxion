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

package io.fluxion.server.core.job.service;

import io.fluxion.remote.core.constants.JobStatus;
import io.fluxion.server.infrastructure.dao.entity.JobEntity;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import java.time.LocalDateTime;

/**
 * Job 下发 lease 和 attempt 的条件更新。
 */
@Service
public class JobLeaseService {

    public static final int LEASE_DURATION_SECONDS = 15;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public Integer claimFirstAttempt(String jobId, String brokerId, LocalDateTime leaseUntil) {
        LocalDateTime now = LocalDateTime.now();
        int updated = entityManager.createQuery("update JobEntity set leaseOwner = :brokerId, leaseUntil = :leaseUntil, "
                + "dispatchAttempt = coalesce(dispatchAttempt, 0) + 1 "
                + "where jobId = :jobId and status = :status and "
                + "(leaseOwner is null or leaseUntil is null or leaseUntil < :now)")
            .setParameter("brokerId", brokerId)
            .setParameter("leaseUntil", leaseUntil)
            .setParameter("jobId", jobId)
            .setParameter("status", JobStatus.INITED.value)
            .setParameter("now", now)
            .executeUpdate();
        return updated == 1 ? currentAttempt(jobId) : null;
    }

    @Transactional
    public Integer nextAttempt(String jobId, String brokerId, int currentAttempt, LocalDateTime leaseUntil) {
        LocalDateTime now = LocalDateTime.now();
        int updated = entityManager.createQuery("update JobEntity set leaseUntil = :leaseUntil, "
                + "dispatchAttempt = dispatchAttempt + 1 "
                + "where jobId = :jobId and status = :status and leaseOwner = :brokerId "
                + "and leaseUntil >= :now and dispatchAttempt = :currentAttempt")
            .setParameter("leaseUntil", leaseUntil)
            .setParameter("jobId", jobId)
            .setParameter("status", JobStatus.INITED.value)
            .setParameter("brokerId", brokerId)
            .setParameter("now", now)
            .setParameter("currentAttempt", currentAttempt)
            .executeUpdate();
        return updated == 1 ? currentAttempt(jobId) : null;
    }

    @Transactional
    public boolean renew(String jobId, String brokerId, int dispatchAttempt, LocalDateTime leaseUntil) {
        return entityManager.createQuery("update JobEntity set leaseUntil = :leaseUntil "
                + "where jobId = :jobId and leaseOwner = :brokerId and dispatchAttempt = :dispatchAttempt "
                + "and leaseUntil >= :now")
            .setParameter("leaseUntil", leaseUntil)
            .setParameter("jobId", jobId)
            .setParameter("brokerId", brokerId)
            .setParameter("dispatchAttempt", dispatchAttempt)
            .setParameter("now", LocalDateTime.now())
            .executeUpdate() == 1;
    }

    @Transactional
    public boolean release(String jobId, String brokerId, int dispatchAttempt) {
        return entityManager.createQuery("update JobEntity set leaseOwner = null, leaseUntil = null "
                + "where jobId = :jobId and leaseOwner = :brokerId and dispatchAttempt = :dispatchAttempt")
            .setParameter("jobId", jobId)
            .setParameter("brokerId", brokerId)
            .setParameter("dispatchAttempt", dispatchAttempt)
            .executeUpdate() == 1;
    }

    @Transactional
    public boolean setTimeout(String jobId, String brokerId, int dispatchAttempt, LocalDateTime timeoutAt) {
        return entityManager.createQuery("update JobEntity set timeoutAt = :timeoutAt "
                + "where jobId = :jobId and leaseOwner = :brokerId and dispatchAttempt = :dispatchAttempt "
                + "and leaseUntil >= :now")
            .setParameter("timeoutAt", timeoutAt)
            .setParameter("jobId", jobId)
            .setParameter("brokerId", brokerId)
            .setParameter("dispatchAttempt", dispatchAttempt)
            .setParameter("now", LocalDateTime.now())
            .executeUpdate() == 1;
    }

    private Integer currentAttempt(String jobId) {
        entityManager.clear();
        JobEntity entity = entityManager.find(JobEntity.class, jobId);
        return entity == null ? null : entity.getDispatchAttempt();
    }
}
