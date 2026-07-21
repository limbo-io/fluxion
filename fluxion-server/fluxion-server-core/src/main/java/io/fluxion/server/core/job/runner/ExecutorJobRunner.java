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

package io.fluxion.server.core.job.runner;

import io.limbo.utils.time.TimeUtils;
import io.fluxion.remote.core.api.Response;
import io.fluxion.remote.core.api.request.worker.JobDispatchRequest;
import io.fluxion.remote.core.constants.WorkerRemoteConstant;
import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.execution.fault.ExecutionInfo;
import io.fluxion.server.core.execution.fault.ExecutionRegistration;
import io.fluxion.server.core.execution.fault.ExecutionState;
import io.fluxion.server.core.execution.fault.FaultToleranceCoordinator;
import io.fluxion.server.core.job.Job;
import io.fluxion.server.core.job.JobType;
import io.fluxion.server.core.job.cmd.JobFailCmd;
import io.fluxion.server.core.job.config.ExecutorJobConfig;
import io.fluxion.server.core.job.query.JobConfigQuery;
import io.fluxion.server.core.worker.Worker;
import io.fluxion.server.core.worker.query.WorkersFilterQuery;
import io.fluxion.server.core.worker.selector.WorkerStatisticsRepository;
import io.limbo.cqrs.spring.command.Cmd;
import io.limbo.cqrs.spring.query.Query;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
@Slf4j
@Component
public class ExecutorJobRunner extends JobRunner {

    /**
     * 最大下发尝试次数
     */
    private static final int MAX_DISPATCH_ATTEMPTS = 3;

    @Resource
    private FaultToleranceCoordinator faultToleranceCoordinator;

    /**
     * 记录每个任务的当前下发尝试次数
     */
    private final Map<String, Integer> dispatchAttempts = new ConcurrentHashMap<>();

    /**
     * 记录当前任务尝试的worker和尝试次数（用于幂等验证）
     */
    private final Map<String, DispatchTarget> dispatchTargets = new ConcurrentHashMap<>();

    @Override
    public JobType type() {
        return JobType.EXECUTOR;
    }

    @Override
    public void run(Job job) {
        ExecutorJobConfig config = (ExecutorJobConfig) Query.query(new JobConfigQuery(job.getExecutionId(), job.getRefId())).getConfig();

        // 获取候选worker列表（按选择器偏好排序，最佳优先）
        List<Worker> candidates = Query.query(new WorkersFilterQuery(
            config.getAppId(), config.getExecutorName(),
            config.getDispatchOption(), true, false, true
        )).getWorkers();

        candidates = CollectionUtils.isEmpty(candidates) ? Collections.emptyList()
            : candidates.stream().filter(Objects::nonNull).collect(Collectors.toList());

        if (candidates.isEmpty()) {
            log.error("[ExecutorJobRunner] No available workers for job: jobId={}, appId={}, executor={}",
                job.getJobId(), config.getAppId(), config.getExecutorName());
            Cmd.send(new JobFailCmd(
                job.getJobId(),
                TimeUtils.currentLocalDateTime(),
                "No available workers for dispatch",
                null
            ));
            return;
        }

        // 尝试下发，失败后按候选顺序尝试下一个
        List<Worker> remainingCandidates = new ArrayList<>(candidates);
        DispatchResult result = tryDispatchToCandidates(job, config, remainingCandidates);

        if (!result.isSuccess()) {
            handleDispatchFailure(job, result);
        }
    }

    /**
     * 尝试向候选worker列表下发任务
     */
    private DispatchResult tryDispatchToCandidates(Job job, ExecutorJobConfig config, List<Worker> candidates) {
        int attemptInThisRun = 0;

        while (!candidates.isEmpty() && attemptInThisRun < MAX_DISPATCH_ATTEMPTS) {
            // 原子递增并获取该任务的累计尝试次数
            int dispatchAttempt = dispatchAttempts.merge(job.getJobId(), 1, Integer::sum);
            attemptInThisRun++;

            Worker worker = candidates.get(0);
            DispatchTarget target = new DispatchTarget(worker.id(), dispatchAttempt);
            dispatchTargets.put(job.getJobId(), target);

            log.info("[ExecutorJobRunner] Dispatch attempt {} to worker {} for job {}",
                dispatchAttempt, worker.id(), job.getJobId());

            DispatchResult result = dispatchToWorker(job, config, worker, dispatchAttempt);

            if (result.isSuccess()) {
                // 记录成功到统计仓库（用于LFU/LRU策略）
                recordDispatchSuccess(worker);
                return result;
            }

            // 下发失败，从候选列表移除该worker并尝试下一个
            log.warn("[ExecutorJobRunner] Dispatch failed to worker {} for job {}: errorType={}",
                worker.id(), job.getJobId(), result.getErrorType());
            candidates.remove(0);
        }

        if (candidates.isEmpty()) {
            return DispatchResult.failure(DispatchErrorType.NO_CANDIDATES, "All candidate workers failed");
        }
        return DispatchResult.failure(DispatchErrorType.MAX_ATTEMPTS_EXCEEDED, "Max dispatch attempts exceeded");
    }

    /**
     * 向指定worker下发任务
     */
    private DispatchResult dispatchToWorker(Job job, ExecutorJobConfig config, Worker worker, int dispatchAttempt) {
        try {
            JobDispatchRequest request = new JobDispatchRequest();
            request.setJobId(job.getJobId());
            request.setExecutorName(config.getExecutorName());
            request.setExecuteMode(config.getExecuteMode().mode);
            request.setDispatchAttempt(dispatchAttempt);

            Response<Boolean> response = BrokerContext.call(
                WorkerRemoteConstant.API_JOB_DISPATCH, worker.getHost(), worker.getPort(), request
            );

            if (!response.success()) {
                // 网络/通信错误
                log.warn("[ExecutorJobRunner] Network error dispatching to worker {}: {}",
                    worker.id(), response.getMessage());
                return DispatchResult.failure(DispatchErrorType.NETWORK_ERROR, response.getMessage());
            }

            if (!BooleanUtils.isTrue(response.getData())) {
                // Worker拒绝执行
                log.warn("[ExecutorJobRunner] Worker {} rejected job {}: returned false",
                    worker.id(), job.getJobId());
                return DispatchResult.failure(DispatchErrorType.WORKER_REJECTION, "Worker rejected the job");
            }

            // 下发成功，注册到容错协调器
            ExecutionInfo executionInfo = ExecutionInfo.builder()
                .executionId(job.getExecutionId())
                .jobId(job.getJobId())
                .taskId(job.getRefId())
                .workerId(worker.id())
                .jobType(JobType.EXECUTOR.name())
                .state(ExecutionState.RUNNING)
                .startTime(System.currentTimeMillis())
                .context(new HashMap<>())
                .build();

            ExecutionRegistration registration = faultToleranceCoordinator.register(executionInfo);
            if (registration.isRegistered()) {
                log.info("[ExecutorJobRunner] Execution registered: executionId={}, jobId={}, worker={}",
                    job.getExecutionId(), job.getJobId(), worker.id());
            }

            return DispatchResult.success(worker);

        } catch (Exception e) {
            log.error("[ExecutorJobRunner] Exception dispatching to worker {} for job {}: {}",
                worker.id(), job.getJobId(), e.getMessage(), e);
            return DispatchResult.failure(DispatchErrorType.EXCEPTION, e.getMessage());
        }
    }

    /**
     * 处理下发失败
     */
    private void handleDispatchFailure(Job job, DispatchResult result) {
        String reason = buildFailureReason(result);
        log.error("[ExecutorJobRunner] Job dispatch failed after all attempts: jobId={}, errorType={}, reason={}",
            job.getJobId(), result.getErrorType(), reason);

        Cmd.send(new JobFailCmd(
            job.getJobId(),
            TimeUtils.currentLocalDateTime(),
            reason,
            null
        ));
    }

    /**
     * 构建失败原因描述
     */
    private String buildFailureReason(DispatchResult result) {
        switch (result.getErrorType()) {
            case NO_CANDIDATES:
                return "All candidate workers failed to accept the job";
            case NETWORK_ERROR:
                return "Network errors prevented dispatch to all candidates: " + result.getMessage();
            case WORKER_REJECTION:
                return "All candidate workers rejected the job";
            case MAX_ATTEMPTS_EXCEEDED:
                return "Maximum dispatch attempts exceeded";
            case EXCEPTION:
                return "Dispatch failed with exception: " + result.getMessage();
            default:
                return "Unknown dispatch failure: " + result.getMessage();
        }
    }

    /**
     * 记录下发成功（用于LFU/LRU统计）
     */
    private void recordDispatchSuccess(Worker worker) {
        try {
            WorkerStatisticsRepository statsRepo = Query.query(
                new io.fluxion.server.core.worker.query.WorkerStatisticsQuery()
            ).getRepository();
            if (statsRepo != null) {
                statsRepo.recordDispatch(worker);
            }
        } catch (Exception e) {
            log.debug("[ExecutorJobRunner] Failed to record dispatch statistics: {}", e.getMessage());
        }
    }

    /**
     * 获取当前有效的下发目标（用于幂等验证）
     */
    public DispatchTarget getCurrentDispatchTarget(String jobId) {
        return dispatchTargets.get(jobId);
    }

    /**
     * 清理任务的dispatch状态
     */
    public void cleanupDispatchState(String jobId) {
        dispatchAttempts.remove(jobId);
        dispatchTargets.remove(jobId);
    }

    /**
     * 下发结果封装
     */
    private static class DispatchResult {
        private final boolean success;
        private final Worker worker;
        private final DispatchErrorType errorType;
        private final String message;

        private DispatchResult(boolean success, Worker worker, DispatchErrorType errorType, String message) {
            this.success = success;
            this.worker = worker;
            this.errorType = errorType;
            this.message = message;
        }

        static DispatchResult success(Worker worker) {
            return new DispatchResult(true, worker, null, null);
        }

        static DispatchResult failure(DispatchErrorType errorType, String message) {
            return new DispatchResult(false, null, errorType, message);
        }

        boolean isSuccess() { return success; }
        Worker getWorker() { return worker; }
        DispatchErrorType getErrorType() { return errorType; }
        String getMessage() { return message; }
    }

    /**
     * 下发错误类型
     */
    private enum DispatchErrorType {
        NO_CANDIDATES,
        NETWORK_ERROR,
        WORKER_REJECTION,
        MAX_ATTEMPTS_EXCEEDED,
        EXCEPTION
    }

    /**
     * 下发目标封装
     */
    public static class DispatchTarget {
        private final String workerId;
        private final int attempt;

        public DispatchTarget(String workerId, int attempt) {
            this.workerId = workerId;
            this.attempt = attempt;
        }

        public String getWorkerId() { return workerId; }
        public int getAttempt() { return attempt; }
    }


}
