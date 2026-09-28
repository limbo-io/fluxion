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

package io.fluxion.test.core.worker;

import io.fluxion.remote.core.api.Response;
import io.fluxion.remote.core.api.request.worker.JobDispatchRequest;
import io.fluxion.remote.core.api.response.broker.JobStateTransitionResponse;
import io.fluxion.remote.core.client.LBClient;
import io.fluxion.remote.core.constants.ExecuteMode;
import io.fluxion.remote.core.constants.JobDispatchResult;
import io.fluxion.remote.core.constants.WorkerRemoteConstant;
import io.fluxion.worker.core.WorkerContext;
import io.fluxion.worker.core.Worker;
import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.remote.WorkerClientHandler;
import io.limbo.utils.json.JacksonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Worker 派发终点的 Deadline 最终检查与三值结果码验证（纯 JVM，无需 Spring/DB）。
 * <p>
 * 领域依据 CONTEXT.md：
 * <ul>
 *   <li>「Execution 激活截止时间」——Worker 在业务代码开始前以本机时钟作最终检查；</li>
 *   <li>「Job 派发结果」——ACCEPTED / REJECTED_DEADLINE（不重试）/ REJECTED_UNAVAILABLE（可容错）；</li>
 *   <li>「Execution 激活截止时间」——已在 Deadline 内接收的任务重传不得制造假拒绝。</li>
 * </ul>
 */
class WorkerDispatchDeadlineTest {

    private WorkerContext workerContext;

    @AfterEach
    void tearDown() {
        if (workerContext != null) {
            workerContext.destroy();
        }
    }

    @Test
    @DisplayName("W1: Deadline 已过：新建 Tracker 前拒绝，返回 REJECTED_DEADLINE；重复下发同 attempt 幂等回放同结果")
    void shouldRejectByDeadlineBeforeCreatingTracker() {
        WorkerClientHandler handler = handlerWithRunningWorker();

        Response<?> first = dispatch(handler, "job-dl-1", 1, System.currentTimeMillis() - 1000);
        assertThat(first.getData()).isEqualTo(JobDispatchResult.REJECTED_DEADLINE);

        // 幂等缓存：同 attempt 重传回放同一确定性结果（不构造 Tracker）
        Response<?> second = dispatch(handler, "job-dl-1", 1, System.currentTimeMillis() - 1000);
        assertThat(second.getData()).isEqualTo(JobDispatchResult.REJECTED_DEADLINE);

        // Worker 从未接收该任务
        assertThat(workerContext.getJobTracker("job-dl-1")).isNull();
    }

    @Test
    @DisplayName("W2: Deadline 内已接收：截止时间过后重传同一 attempt 仍返回 ACCEPTED，不制造假拒绝")
    void shouldReturnCachedAcceptedForDuplicateAfterDeadlinePassed() throws InterruptedException {
        WorkerClientHandler handler = handlerWithRunningWorker();
        long deadline = System.currentTimeMillis() + 200;

        Response<?> first = dispatch(handler, "job-dl-2", 1, deadline);
        assertThat(first.getData()).isEqualTo(JobDispatchResult.ACCEPTED);
        assertThat(workerContext.getJobTracker("job-dl-2")).isNotNull();

        // 等待越过 Deadline 后重传（Broker 超时重发/响应丢失场景）
        while (System.currentTimeMillis() <= deadline) {
            Thread.sleep(20);
        }

        Response<?> second = dispatch(handler, "job-dl-2", 1, deadline);
        assertThat(second.getData()).isEqualTo(JobDispatchResult.ACCEPTED);
    }

    @Test
    @DisplayName("W3: Worker 未运行：返回 REJECTED_UNAVAILABLE；且不落任何 Tracker")
    void shouldRejectUnavailableWhenWorkerNotRunning() {
        WorkerClientHandler handler = handlerWithRunningWorker();
        // 强制回到非运行态
        workerContext.status(Worker.Status.RUNNING, Worker.Status.TERMINATED);

        Response<?> response = dispatch(handler, "job-dl-3", 1, null);
        assertThat(response.getData()).isEqualTo(JobDispatchResult.REJECTED_UNAVAILABLE);
        assertThat(workerContext.getJobTracker("job-dl-3")).isNull();

        // 同 attempt 重传：幂等回放缓存的不可用结果
        assertThat(dispatch(handler, "job-dl-3", 1, null).getData())
            .isEqualTo(JobDispatchResult.REJECTED_UNAVAILABLE);
    }

    @Test
    @DisplayName("W4: 结果码 RPC 序列化：value 字符串过线，parse 可逆")
    void serializeDispatchResultOverWireFormat() {
        // RPC 通道上结果码以 value 字符串传输（对齐 JobStatus 等既有惯例）
        Response<JobDispatchResult> resp = Response.ok(JobDispatchResult.REJECTED_DEADLINE);
        assertThat(JacksonUtils.toJSONString(resp)).contains("rejected_deadline");
        assertThat(JacksonUtils.toJSONString(JobDispatchResult.ACCEPTED)).isEqualTo("\"accepted\"");
        assertThat(JobDispatchResult.parse("rejected_unavailable"))
            .isEqualTo(JobDispatchResult.REJECTED_UNAVAILABLE);
    }

    /**
     * 构造一个 RUNNING 状态的真实 WorkerContext + 处理器。
     * brokerClient 以 Mockito stub（状态迁移上报统一回 success），业务执行器仅计数。
     */
    private WorkerClientHandler handlerWithRunningWorker() {
        AtomicInteger ran = new AtomicInteger();
        Executor echoExecutor = new Executor() {
            @Override
            public String name() {
                return "echo";
            }

            @Override
            public void run(io.fluxion.worker.core.task.TaskContext context) {
                ran.incrementAndGet();
            }
        };

        LBClient brokerClient = mock(LBClient.class);
        JobStateTransitionResponse ok = new JobStateTransitionResponse();
        ok.setSuccess(true);
        when(brokerClient.call(anyString(), any())).thenReturn(Response.ok(ok));

        workerContext = new WorkerContext("test-worker", io.fluxion.remote.core.constants.Protocol.HTTP,
            "127.0.0.1", 39999, 10, 2, brokerClient,
            Collections.singletonList(echoExecutor), Collections.emptyMap());
        workerContext.initialize();
        assertThat(workerContext.status(Worker.Status.IDLE, Worker.Status.RUNNING)).isTrue();
        return new WorkerClientHandler(workerContext, null);
    }

    private Response<?> dispatch(WorkerClientHandler handler, String jobId, int dispatchAttempt, Long deadlineEpochMillis) {
        JobDispatchRequest request = new JobDispatchRequest();
        request.setJobId(jobId);
        request.setExecutorName("echo");
        request.setExecuteMode(ExecuteMode.STANDALONE.mode);
        request.setDispatchAttempt(dispatchAttempt);
        request.setDeadlineEpochMillis(deadlineEpochMillis);
        return handler.process(WorkerRemoteConstant.API_JOB_DISPATCH, JacksonUtils.toJSONString(request));
    }
}
