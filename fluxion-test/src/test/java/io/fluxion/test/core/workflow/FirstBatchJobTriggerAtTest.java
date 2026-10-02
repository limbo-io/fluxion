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

package io.fluxion.test.core.workflow;

import io.fluxion.remote.core.api.request.worker.JobDispatchRequest;
import io.fluxion.remote.core.constants.ExecuteMode;
import io.fluxion.server.core.broker.cmd.BucketAllotCmd;
import io.fluxion.server.core.execution.Execution;
import io.fluxion.server.core.execution.ExecutionStatus;
import io.fluxion.server.core.execution.ExecutableType;
import io.fluxion.server.core.executor.Executor;
import io.fluxion.server.core.executor.config.ShellScriptExecutorConfig;
import io.fluxion.server.core.job.cmd.JobRunCmd;
import io.fluxion.server.core.job.cmd.JobsCreateCmd;
import io.limbo.cqrs.core.commandhandling.Cmd;
import io.limbo.cqrs.core.commandhandling.CommandBus;
import io.limbo.cqrs.core.commandhandling.HandlerRegistration;
import io.limbo.cqrs.core.commandhandling.ICommand;
import io.limbo.cqrs.core.message.CommandMessage;
import io.limbo.cqrs.core.message.CommandResultMessage;
import io.fluxion.server.core.workflow.HandleEdge;
import io.fluxion.server.core.workflow.Workflow;
import io.fluxion.server.core.workflow.WorkflowConfig;
import io.fluxion.server.core.workflow.node.EndNode;
import io.fluxion.server.core.workflow.node.ExecutorNode;
import io.fluxion.server.core.workflow.node.StartNode;
import io.fluxion.server.core.workflow.node.WorkflowNode;
import io.fluxion.worker.core.job.Job;
import io.fluxion.worker.core.job.JobContext;
import io.fluxion.worker.core.remote.WorkerClientConverter;
import io.limbo.utils.json.JacksonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Job 的原始计划触发时刻的全链路语义（见 CONTEXT.md「Job 时间语义」）：
 * <ul>
 *   <li>调度 Execution 的首批 Job（单执行器、Workflow 根节点）继承 Execution.triggerAt；</li>
 *   <li>Workflow 下游 Job 以自身就绪时刻为 triggerAt，不继承；</li>
 *   <li>triggerAt 以 UTC epoch millis 过线并在 Worker 的 Job/JobContext 上原样暴露；
 *       接收时刻是 Worker 本机事实，不由 Broker 提供。</li>
 * </ul>
 * 纯 JVM 测试：以捕获型 mock CommandBus 替换静态门面，方法级保存/恢复，不污染其它测试。
 */
@DisplayName("First-batch job triggerAt semantics")
class FirstBatchJobTriggerAtTest {

    private CommandBus previousBus;

    @AfterEach
    void tearDown() {
        // 恢复原静态门面，避免污染其它测试（Spring 上下文或后续 POJO 测试）
        Cmd.reset(previousBus);
    }

    @Test
    @DisplayName("单执行器 Execution：首批 Job inherit Execution.triggerAt（null 时退化为本机时刻）")
    void executorFirstJobInheritsExecutionTriggerAt() {
        List<Object> commands = installCapturingBus();
        Instant scheduledAt = Instant.now().minusSeconds(120);

        Executor executor = Executor.of("executor-1", "v1", new ShellScriptExecutorConfig(), null, null);
        executor.execute(new Execution("execution-1", ExecutionStatus.CLAIMED,
            "executor-1", "v1", ExecutableType.EXECUTOR, null, scheduledAt));

        JobsCreateCmd created = firstOf(commands, JobsCreateCmd.class);
        assertThat(created.getJobs()).hasSize(1);
        assertThat(created.getJobs().get(0).getTriggerAt()).isEqualTo(scheduledAt);
        // Deadline 快照仍从 Execution 复制：triggerAt 是计划时刻、deadlineAt 是激活上限
        assertThat(created.getJobs().get(0).getDeadlineAt()).isNull();
    }

    @Test
    @DisplayName("Workflow 首批 Job 继承 Execution.triggerAt；下游 Job 用自身就绪时刻")
    void workflowFirstBatchInheritsAndDownstreamSelfDates() {
        List<Object> commands = installCapturingBus();
        Instant scheduledAt = Instant.now().minusSeconds(300);

        Workflow workflow = Workflow.of("workflow-1", "v1",
            workflowConfig("start-1", "task-1", "end-1"));
        workflow.execute(new Execution("execution-2", ExecutionStatus.CLAIMED,
            "workflow-1", "v1", ExecutableType.WORKFLOW, null, scheduledAt));

        JobsCreateCmd firstBatch = onlyOf(commands, JobsCreateCmd.class);
        // 首批（根 START 节点）继承 Execution 原始计划触发时刻
        assertThat(firstBatch.getJobs()).hasSize(1);
        assertThat(firstBatch.getJobs().get(0).getRefId()).isEqualTo("start-1");
        assertThat(firstBatch.getJobs().get(0).getTriggerAt()).isEqualTo(scheduledAt);

        // START 完成推进下游：下游 Job 的 triggerAt 是自身就绪时刻，不继承计划时刻
        workflow.success("execution-2", "start-1", Instant.now());

        JobsCreateCmd downstream = lastOf(commands, JobsCreateCmd.class);
        assertThat(downstream.getJobs()).isNotEmpty();
        assertThat(downstream.getJobs().get(0).getRefId()).isEqualTo("task-1");
        Instant downstreamTriggerAt = downstream.getJobs().get(0).getTriggerAt();
        assertThat(downstreamTriggerAt).isAfterOrEqualTo(scheduledAt);
        assertThat(downstreamTriggerAt).isNotEqualTo(scheduledAt);
        assertThat(downstreamTriggerAt.plusSeconds(60)).isAfter(Instant.now());
    }

    @Test
    @DisplayName("triggerAt 过 Worker 线：epoch millis 整数过线并在 Job/JobContext 原样暴露")
    void triggerAtTravelsWorkerWireAndExposesInContext() {
        Instant scheduledAt = Instant.ofEpochMilli(1_767_225_600_000L);
        JobDispatchRequest request = new JobDispatchRequest();
        request.setJobId("job-w");
        request.setExecutorName("echo");
        request.setExecuteMode(ExecuteMode.STANDALONE.mode);
        request.setDispatchAttempt(1);
        request.setTriggerAt(scheduledAt.toEpochMilli());

        // RPC JSON 是毫秒整数——不是十进制秒、不是 ISO 字符串（TimeWire 约定）
        String json = JacksonUtils.toJSONString(request);
        assertThat(json).contains("\"triggerAt\":1767225600000");

        Job job = WorkerClientConverter.toJob(request);
        assertThat(job.getTriggerAt()).isEqualTo(scheduledAt);
        JobContext context = JobContext.from(job);
        assertThat(context.getTriggerAt()).isEqualTo(scheduledAt);

        // 缺省（非调度来源/旧版本 Broker）：保持 null，不臆造时刻
        JobDispatchRequest absent = new JobDispatchRequest();
        absent.setJobId("job-legacy");
        assertThat(WorkerClientConverter.toJob(absent).getTriggerAt()).isNull();
        assertThat(JobContext.from(WorkerClientConverter.toJob(absent)).getTriggerAt()).isNull();
    }

    // ==================== helpers ====================

    /**
     * 安装捕获型 CommandBus，并保存原静态门面以便恢复。
     * 实现而非 Mockito 打桩：dispatch 两个重载在 any() 匹配下有歧义。
     */
    private List<Object> installCapturingBus() {
        List<Object> captured = new ArrayList<>();
        CommandBus bus = new CommandBus() {
            @Override
            @SuppressWarnings("unchecked")
            public <C extends ICommand<R>, R> R dispatch(C command) {
                captured.add(command);
                if (command instanceof BucketAllotCmd) {
                    return (R) new BucketAllotCmd.Response(1);
                }
                return null;
            }

            @Override
            public <C extends ICommand<R>, R> CompletableFuture<R> dispatchAsync(C command) {
                throw new UnsupportedOperationException();
            }

            @Override
            @SuppressWarnings("unchecked")
            public <C extends ICommand<R>, R> CommandResultMessage<R> dispatch(CommandMessage<C> message) {
                captured.add(message.getPayload());
                return new CommandResultMessage<>(message.getIdentifier(), null, null);
            }

            @Override
            public <C extends ICommand<R>, R> HandlerRegistration register(Class<C> payloadType,
                io.limbo.cqrs.core.commandhandling.CommandHandler<C, R> handler) {
                return HandlerRegistration.of(() -> { });
            }

            @Override
            @SuppressWarnings("unchecked")
            public <C extends ICommand<R>, R> HandlerRegistration registerMulti(Class<C>[] payloadTypes,
                io.limbo.cqrs.core.commandhandling.CommandHandler<C, R> handler) {
                return HandlerRegistration.of(() -> { });
            }

            @Override
            public void shutdown() {
                // 纯捕获总线：无资源可释放
            }
        };
        previousBus = currentBus();
        Cmd.init(bus);
        return captured;
    }

    private JobsCreateCmd firstOf(List<Object> commands, Class<JobsCreateCmd> type) {
        return commands.stream().filter(type::isInstance).map(type::cast).findFirst()
            .orElseThrow(() -> new IllegalStateException("no JobsCreateCmd captured"));
    }

    private JobsCreateCmd onlyOf(List<Object> commands, Class<JobsCreateCmd> type) {
        List<JobsCreateCmd> all = allOf(commands, type);
        assertThat(all).hasSize(1);
        return all.get(0);
    }

    private JobsCreateCmd lastOf(List<Object> commands, Class<JobsCreateCmd> type) {
        List<JobsCreateCmd> all = allOf(commands, type);
        assertThat(all).isNotEmpty();
        return all.get(all.size() - 1);
    }

    private List<JobsCreateCmd> allOf(List<Object> commands, Class<JobsCreateCmd> type) {
        List<JobsCreateCmd> result = new ArrayList<>();
        for (Object command : commands) {
            if (type.isInstance(command)) {
                result.add(type.cast(command));
            }
        }
        return result;
    }

    private WorkflowConfig workflowConfig(String startId, String taskId, String endId) {
        WorkflowConfig config = new WorkflowConfig();
        StartNode start = new StartNode();
        start.setId(startId);
        start.setName(startId);
        start.setType(WorkflowNode.Type.START);

        ExecutorNode task = new ExecutorNode();
        task.setId(taskId);
        task.setName(taskId);
        task.setType(WorkflowNode.Type.EXECUTOR);
        task.setExecutor(new ShellScriptExecutorConfig());

        EndNode end = new EndNode();
        end.setId(endId);
        end.setName(endId);
        end.setType(WorkflowNode.Type.END);

        config.setNodes(new ArrayList<>(List.of(start, task, end)));
        List<HandleEdge> edges = new ArrayList<>();
        edges.add(edge(startId, taskId));
        edges.add(edge(taskId, endId));
        config.setEdges(edges);
        return config;
    }

    private HandleEdge edge(String source, String target) {
        HandleEdge edge = new HandleEdge();
        edge.setSourceNodeId(source);
        edge.setTargetNodeId(target);
        return edge;
    }

    private static CommandBus currentBus() {
        try {
            Field field = Cmd.class.getDeclaredField("commandBus");
            field.setAccessible(true);
            return (CommandBus) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}