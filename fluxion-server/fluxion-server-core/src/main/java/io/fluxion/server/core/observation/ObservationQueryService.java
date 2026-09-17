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

package io.fluxion.server.core.observation;

import io.fluxion.remote.core.constants.JobStatus;
import io.fluxion.server.core.execution.ExecutionStatus;
import io.fluxion.server.core.execution.service.ExecutionScheduleCommandService;
import io.fluxion.server.core.worker.Worker;
import io.fluxion.server.infrastructure.dao.entity.ExecutionEntity;
import io.fluxion.server.infrastructure.dao.entity.JobEntity;
import io.fluxion.server.infrastructure.dao.entity.WorkerEntity;
import io.limbo.cqrs.spring.annotation.QueryHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 存量观测查询：对 MySQL 单点权威做只读聚合。所有状态 key 均为领域枚举的存储值
 * （见 CONTEXT.md「Execution 状态」「Job 状态」），zero-fill 保证分布键稳定。
 *
 * @author Devil
 */
@Slf4j
@Service
public class ObservationQueryService {

    private static final String PENDING = ExecutionStatus.PENDING.value;
    private static final String CLAIMED = ExecutionStatus.CLAIMED.value;

    @Resource
    private EntityManager entityManager;

    @QueryHandler
    public ObservationOverviewQuery.Response handle(ObservationOverviewQuery query) {
        LocalDateTime now = LocalDateTime.now();

        Map<String, Long> executions = statusDistribution(ExecutionEntity.class, ExecutionStatus.values(),
            ExecutionStatus.UNKNOWN);
        long backlog = countPendingDue(now);
        long misfireCandidates = countMisfireCandidates(now);
        long reclaimableClaims = countReclaimableClaims(now);
        Map<String, Long> jobs = statusDistribution(JobEntity.class, JobStatus.values(), JobStatus.UNKNOWN);
        Map<String, Long> workers = statusDistribution(WorkerEntity.class, Worker.Status.values(),
            Worker.Status.UNKNOWN);

        return new ObservationOverviewQuery.Response(
            executions, backlog, misfireCandidates, reclaimableClaims, jobs, workers);
    }

    /** 状态计数（zero-fill 枚举值并排除 UNKNOWN；库中若出现未识别状态值也会原样保留） */
    private Map<String, Long> statusDistribution(Class<?> entity, Enum<?>[] statuses, Enum<?> unknown) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Enum<?> status : statuses) {
            if (status == unknown) {
                continue;
            }
            result.put(statusValue(status), 0L);
        }
        List<Object[]> rows = entityManager.createQuery(
            "select e.status, count(e) from " + entity.getSimpleName() + " e"
                + " where e.deleted = false group by e.status", Object[].class)
            .getResultList();
        for (Object[] row : rows) {
            result.merge(String.valueOf(row[0]), (Long) row[1], Long::sum);
        }
        return result;
    }

    /** 积压：已到triggerAt 但尚未 claim 的 PENDING */
    private long countPendingDue(LocalDateTime now) {
        return countExecutions(PENDING, "e.triggerAt <= :time", now);
    }

    /** 错过触发候选：now - triggerAt > misfireThreshold 的 PENDING */
    private long countMisfireCandidates(LocalDateTime now) {
        LocalDateTime threshold = now.minusSeconds(ExecutionScheduleCommandService.MISFIRE_THRESHOLD_SECONDS);
        return countExecutions(PENDING, "e.triggerAt < :time", threshold);
    }

    /** 可回收租约：CLAIMED 且租约已过期，下一轮 ExecutionsLoadCmd 可重新领取 */
    private long countReclaimableClaims(LocalDateTime now) {
        return countExecutions(CLAIMED, "e.leaseUntil <= :time", now);
    }

    private long countExecutions(String status, String condition, LocalDateTime time) {
        return entityManager.createQuery(
            "select count(e) from ExecutionEntity e"
                + " where e.deleted = false and e.status = :status and " + condition, Long.class)
            .setParameter("status", status)
            .setParameter("time", time)
            .getSingleResult();
    }

    private String statusValue(Enum<?> status) {
        if (status instanceof ExecutionStatus) {
            return ((ExecutionStatus) status).value;
        }
        if (status instanceof JobStatus) {
            return ((JobStatus) status).value;
        }
        if (status instanceof Worker.Status) {
            return ((Worker.Status) status).status;
        }
        return status.name().toLowerCase();
    }
}