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

package io.fluxion.server.core.workflow;

import io.fluxion.common.thread.CommonThreadPool;
import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.remote.core.constants.JobStatus;
import io.fluxion.server.core.context.RunContext;
import io.fluxion.server.core.execution.Executable;
import io.fluxion.server.core.execution.ExecutableType;
import io.fluxion.server.core.execution.Execution;
import io.fluxion.server.core.execution.cmd.ExecutionFailCmd;
import io.fluxion.server.core.execution.cmd.ExecutionSuccessCmd;
import io.fluxion.server.core.executor.config.ExecutorConfig;
import io.fluxion.server.core.executor.option.RetryOption;
import io.fluxion.server.core.job.ExecutorJob;
import io.fluxion.server.core.job.InputOutputJob;
import io.fluxion.server.core.job.Job;
import io.fluxion.server.core.job.cmd.JobRunCmd;
import io.fluxion.server.core.job.cmd.JobsCreateCmd;
import io.fluxion.server.core.job.query.JobCountByStatusQuery;
import io.fluxion.server.core.workflow.node.EndNode;
import io.fluxion.server.core.workflow.node.ExecutorNode;
import io.fluxion.server.core.workflow.node.StartNode;
import io.fluxion.server.core.workflow.node.WorkflowNode;
import io.fluxion.server.infrastructure.concurrent.LoggingTask;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.dag.DAG;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Flow 运行态
 *
 * @author Devil
 */
@Slf4j
public class Workflow implements Executable {

    private String id;

    private String version;

    /**
     * 使用 dag()
     * 1. 入口必须为触发类型
     * 2. 普通节点的父节点可以是触发节点/普通节点
     * 3. 触发节点的字节点不能是触发节点
     */
    private DAG<WorkflowNode> dag;

    private Workflow() {
    }

    private Workflow(String id, String version, WorkflowConfig config) {
        this.id = id;
        this.version = version;
        this.dag = new DAG<>(config.getNodes(), config.getEdges());
    }

    public static Workflow of(String id, String version, WorkflowConfig config) {
        return new Workflow(id, version, config);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public ExecutableType type() {
        return ExecutableType.WORKFLOW;
    }

    @Override
    public void execute(RunContext context) {
        createAndScheduleTasks(context.execution(), dag.origins());
    }

    /**
     * 某个 node 成功后执行的逻辑
     */
    @Override
    public boolean success(Job job, LocalDateTime time) {
        Execution execution = job.getExecution();
        List<WorkflowNode> subNodes = dag.subNodes(job.getRefId());
        if (CollectionUtils.isEmpty(subNodes)) {
            // 最终节点 execution 完成
            return Cmd.send(new ExecutionSuccessCmd(execution.getId(), time));
        }
        List<WorkflowNode> continueNodes = new ArrayList<>();
        for (WorkflowNode subNode : subNodes) {
            if (preNodesSuccess(execution.getId(), dag.preNodes(subNode.id()))) {
                // 前置节点都已经完成，下发
                continueNodes.add(subNode);
            }
            // 后续节点依赖其它节点完成，交由其它节点触发
        }
        if (CollectionUtils.isEmpty(continueNodes)) {
            return true;
        }
        createAndScheduleTasks(execution, continueNodes);
        return true;
    }

    @Override
    public boolean fail(Job job, LocalDateTime time) {
        WorkflowNode node = dag.node(job.getRefId());
        if (node.isSkipWhenFail()) {
            return success(job, time);
        } else {
            return Cmd.send(new ExecutionFailCmd(job.getExecution().getId(), time));
        }
    }

    @Override
    public RetryOption retryOption(String refId) {
        WorkflowNode node = dag.node(refId);
        return node.getRetryOption();
    }

    private void createAndScheduleTasks(Execution execution, List<WorkflowNode> nodes) {
        LocalDateTime now = TimeUtils.currentLocalDateTime();
        List<Job> jobs = nodes.stream()
            .map(n -> {
                Job job = newRefJob(n.getId());
                job.setExecution(execution);
                job.setTriggerAt(now);
                return job;
            })
            .collect(Collectors.toList());
        // 保存数据
        Cmd.send(new JobsCreateCmd(jobs));
        // 执行
        for (Job job : jobs) {
            CommonThreadPool.IO.submit(new LoggingTask(() -> Cmd.send(new JobRunCmd(job))));
        }
    }

    private boolean preNodesSuccess(String executionId, List<WorkflowNode> preNodes) {
        if (CollectionUtils.isEmpty(preNodes)) {
            return true; // 没有前置节点，只有start节点才会有
        }
        if (preNodes.size() == 1) {
            return true; // 之前的节点完成了，没有其它节点了
        }
        long count = Query.query(new JobCountByStatusQuery(
            executionId, preNodes.stream().map(WorkflowNode::getId).collect(Collectors.toList()),
            Collections.singletonList(JobStatus.SUCCEED)
        )).getCount();
        return count >= preNodes.size();
    }

    @Override
    public Job newRefJob(String refId) {
        Job job = null;
        WorkflowNode node = dag.node(refId);
        if (node instanceof StartNode) {
            job = new InputOutputJob();
        } else if (node instanceof EndNode) {
            job = new InputOutputJob();
        } else if (node instanceof ExecutorNode) {
            ExecutorNode executorNode = (ExecutorNode) node;
            job = new ExecutorJob();
            ExecutorJob executorTask = (ExecutorJob) job;
            ExecutorConfig executorConfig = executorNode.getExecutor();
            executorTask.setAppId(executorConfig.getAppId());
            executorTask.setExecutorName(executorConfig.executorName());
            executorTask.setDispatchOption(executorConfig.getDispatchOption());
            executorTask.setExecuteMode(executorConfig.getExecuteMode());
        }
        job.setRefId(node.id());
        job.setRetryOption(node.getRetryOption());
        return job;
    }

}
