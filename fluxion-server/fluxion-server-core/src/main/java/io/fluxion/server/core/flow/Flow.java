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

package io.fluxion.server.core.flow;

import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.server.core.context.RunContext;
import io.fluxion.server.core.execution.Executable;
import io.fluxion.server.core.execution.cmd.ExecutionFailCmd;
import io.fluxion.server.core.execution.cmd.ExecutionSuccessCmd;
import io.fluxion.server.core.executor.config.ExecutorConfig;
import io.fluxion.server.core.flow.node.EndNode;
import io.fluxion.server.core.flow.node.ExecutorNode;
import io.fluxion.server.core.flow.node.FlowNode;
import io.fluxion.server.core.flow.node.StartNode;
import io.fluxion.server.core.task.ExecutorTask;
import io.fluxion.server.core.task.InputOutputTask;
import io.fluxion.server.core.task.Task;
import io.fluxion.server.core.task.TaskStatus;
import io.fluxion.server.core.task.cmd.TaskFailCmd;
import io.fluxion.server.core.task.cmd.TaskRetryCmd;
import io.fluxion.server.core.task.cmd.TaskSuccessCmd;
import io.fluxion.server.core.task.cmd.TasksCreateCmd;
import io.fluxion.server.core.task.query.TaskCountByStatusQuery;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.dag.DAG;
import io.fluxion.server.infrastructure.validata.ValidatableConfig;
import lombok.Getter;
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
public class Flow implements Executable, ValidatableConfig {

    @Getter
    private String id;

    /**
     * 使用 dag()
     * 1. 入口必须为触发类型
     * 2. 普通节点的父节点可以是触发节点/普通节点
     * 3. 触发节点的字节点不能是触发节点
     */
    private DAG<FlowNode> dag;

    private Flow() {
    }

    public static Flow of(String id, FlowConfig config) {
        Flow flow = new Flow();
        flow.id = id;
        flow.dag = new DAG<>(config.getNodes(), config.getEdges());
        return flow;
    }

    @Override
    public void execute(RunContext context) {
        createAndScheduleTasks(context.executionId(), dag.origins());
    }

    /**
     * 某个 node 成功后执行的逻辑
     */
    public boolean success(String nodeId, Task task, String workerAddress, LocalDateTime time) {
        Boolean success = Cmd.send(new TaskSuccessCmd(task.getTaskId(), workerAddress, time));
        if (!success) {
            return false;
        }
        List<FlowNode> subNodes = dag.subNodes(nodeId);
        if (CollectionUtils.isEmpty(subNodes)) {
            // 最终节点 execution 完成
            return Cmd.send(new ExecutionSuccessCmd(task.getExecutionId(), time));
        }
        List<FlowNode> continueNodes = new ArrayList<>();
        for (FlowNode subNode : subNodes) {
            if (preNodesSuccess(task.getExecutionId(), dag.preNodes(subNode.id()))) {
                // 前置节点都已经完成，下发
                continueNodes.add(subNode);
            }
            // 后续节点依赖其它节点完成，交由其它节点触发
        }
        if (CollectionUtils.isEmpty(continueNodes)) {
            return true;
        }
        createAndScheduleTasks(task.getExecutionId(), continueNodes);
        return true;
    }

    private void createAndScheduleTasks(String executionId, List<FlowNode> nodes) {
        LocalDateTime now = TimeUtils.currentLocalDateTime();
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

    private boolean preNodesSuccess(String executionId, List<FlowNode> preNodes) {
        if (CollectionUtils.isEmpty(preNodes)) {
            return true; // 没有前置节点，只有start节点才会有
        }
        if (preNodes.size() == 1) {
            return true; // 之前的节点完成了，没有其它节点了
        }
        int count = Query.query(new TaskCountByStatusQuery(
            Collections.singletonList(TaskStatus.SUCCEED),
            executionId, preNodes.stream().map(FlowNode::getId).collect(Collectors.toList())
        )).getCount();
        return count >= preNodes.size();
    }

    /**
     * 某个 node 失败后执行的逻辑
     */
    public boolean fail(String nodeId, Task task, String workerAddress, LocalDateTime time, String errorMsg) {
        if (task.canRetry()) {
            return Cmd.send(new TaskRetryCmd());
        } else if (task.isSkipWhenFail()) {
            return success(nodeId, task, workerAddress, time);
        } else {
            boolean failed = Cmd.send(new TaskFailCmd(task.getTaskId(), workerAddress, time, errorMsg));
            if (!failed) {
                return false;
            }
            return Cmd.send(new ExecutionFailCmd(task.getExecutionId(), time));
        }
    }

    private Task nodeTask(FlowNode node, String executionId, LocalDateTime triggerAt) {
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
        task.setOvertimeOption(node.getOvertimeOption());
        task.setRetryOption(node.getRetryOption());
        task.setExecutionId(executionId);
        task.setTriggerAt(triggerAt);
        task.setRefId(node.id());
        return task;
    }

}
