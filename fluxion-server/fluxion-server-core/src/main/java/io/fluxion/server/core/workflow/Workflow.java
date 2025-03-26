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

import io.fluxion.server.core.context.RunContext;
import io.fluxion.server.core.execution.Executable;
import io.fluxion.server.core.execution.cmd.ExecutionSuccessCmd;
import io.fluxion.server.core.executor.config.ExecutorConfig;
import io.fluxion.server.core.executor.option.RetryOption;
import io.fluxion.server.core.workflow.node.EndNode;
import io.fluxion.server.core.workflow.node.ExecutorNode;
import io.fluxion.server.core.workflow.node.WorkflowNode;
import io.fluxion.server.core.workflow.node.StartNode;
import io.fluxion.server.core.task.ExecutorTask;
import io.fluxion.server.core.task.InputOutputTask;
import io.fluxion.server.core.task.Task;
import io.fluxion.server.core.task.TaskStatus;
import io.fluxion.server.core.task.cmd.TaskSuccessCmd;
import io.fluxion.server.core.task.cmd.TasksCreateCmd;
import io.fluxion.server.core.task.query.TaskCountByStatusQuery;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.dag.DAG;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Flow 运行态
 *
 * @author Devil
 */
public class Workflow implements Executable {

    @Getter
    private String id;

    /**
     * 使用 dag()
     * 1. 入口必须为触发类型
     * 2. 普通节点的父节点可以是触发节点/普通节点
     * 3. 触发节点的字节点不能是触发节点
     */
    private DAG<WorkflowNode> dag;

    private Workflow() {
    }

    private Workflow(String id, WorkflowConfig config) {
        this.id = id;
        this.dag = new DAG<>(config.getNodes(), config.getEdges());
    }

    public static Workflow of(String id, WorkflowConfig config) {
        return new Workflow(id, config);
    }

    @Override
    public void execute(RunContext context) {
        createAndScheduleTasks(context.executionId(), dag.origins());
    }

    /**
     * 某个 node 成功后执行的逻辑
     */
    @Override
    public boolean success(String nodeId, String taskId, String executionId, String workerAddress, Long time) {
        Boolean success = Cmd.send(new TaskSuccessCmd(taskId, workerAddress, time));
        if (!success) {
            return false;
        }
        List<WorkflowNode> subNodes = dag.subNodes(nodeId);
        if (CollectionUtils.isEmpty(subNodes)) {
            // 最终节点 execution 完成
            return Cmd.send(new ExecutionSuccessCmd(executionId, time));
        }
        List<WorkflowNode> continueNodes = new ArrayList<>();
        for (WorkflowNode subNode : subNodes) {
            if (preNodesSuccess(executionId, dag.preNodes(subNode.id()))) {
                // 前置节点都已经完成，下发
                continueNodes.add(subNode);
            }
            // 后续节点依赖其它节点完成，交由其它节点触发
        }
        if (CollectionUtils.isEmpty(continueNodes)) {
            return true;
        }
        createAndScheduleTasks(executionId, continueNodes);
        return true;
    }

    @Override
    public RetryOption retryOption(String refId) {
        WorkflowNode node = dag.node(refId);
        return node.getRetryOption();
    }

    private void createAndScheduleTasks(String executionId, List<WorkflowNode> nodes) {
        long now = System.currentTimeMillis();
        List<Task> tasks = nodes.stream()
            .map(n -> nodeTask(n, executionId, now))
            .collect(Collectors.toList());
        // 保存数据
        Cmd.send(new TasksCreateCmd(tasks));
        // 执行
        for (Task task : tasks) {
            task.schedule();
        }
    }

    private boolean preNodesSuccess(String executionId, List<WorkflowNode> preNodes) {
        if (CollectionUtils.isEmpty(preNodes)) {
            return true; // 没有前置节点，只有start节点才会有
        }
        if (preNodes.size() == 1) {
            return true; // 之前的节点完成了，没有其它节点了
        }
        int count = Query.query(new TaskCountByStatusQuery(
            Collections.singletonList(TaskStatus.SUCCEED),
            executionId, preNodes.stream().map(WorkflowNode::getId).collect(Collectors.toList())
        )).getCount();
        return count >= preNodes.size();
    }

    private Task nodeTask(WorkflowNode node, String executionId, Long triggerAt) {
        Task task = null;
        if (node instanceof StartNode) {
            task = new InputOutputTask();
        } else if (node instanceof EndNode) {
            task = new InputOutputTask();
        } else if (node instanceof ExecutorNode) {
            ExecutorNode executorNode = (ExecutorNode) node;
            task = new ExecutorTask();
            ExecutorTask executorTask = (ExecutorTask) task;
            ExecutorConfig executorConfig = executorNode.getExecutor();
            executorTask.setAppId(executorConfig.getAppId());
            executorTask.setExecutorName(executorConfig.executorName());
            executorTask.setDispatchOption(executorConfig.getDispatchOption());
            executorTask.setExecuteMode(executorConfig.getExecuteMode());
        }
        task.setExecutionId(executionId);
        task.setTriggerAt(triggerAt);
        task.setRefId(node.id());
        return task;
    }

}
