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

import io.fluxion.server.core.execution.Executable;
import io.fluxion.server.core.execution.cmd.ExecutionCreateCmd;
import io.fluxion.server.core.flow.node.FlowNode;
import io.fluxion.server.core.flow.node.StartNode;
import io.fluxion.server.core.task.Task;
import io.fluxion.server.core.task.TaskRefType;
import io.fluxion.server.core.task.cmd.TaskBatchCreateCmd;
import io.fluxion.server.core.trigger.Trigger;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.dag.DAG;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Flow 运行态
 *
 * @author Devil
 */
@AllArgsConstructor
public class Flow implements Executable {

    @Getter
    private String id;

    /**
     * 所有节点
     * 1. 入口必须为触发类型
     * 2. 普通节点的父节点可以是触发节点/普通节点
     * 3. 触发节点的字节点不能是触发节点
     */
    private DAG<FlowNode> dag;

    /**
     * 执行某个节点
     */
    public void execute(String executionId, String nodeId) {
        // 判断是否已经创建task
        Task task = null; // todo
        if (task == null) {
            // todo create
        }

        // 判断前置节点执行状态，是否能执行当前节点
        List<FlowNode> parentNodes = dag.preNodes(nodeId);


        FlowNode currentNode = dag.node(nodeId);
//        Output output = currentNode.run(null);

        // 保存结果和output

        // 基于execution创建 task
        List<FlowNode> subNodes = dag.subNodes(nodeId);
        for (FlowNode subNode : subNodes) {
//            execute(subNode.getId());
        }
    }

    @Override
    public void execute(LocalDateTime triggerAt) {
        ExecutionCreateCmd createCmd = new ExecutionCreateCmd(id, Trigger.RefType.FLOW, id, triggerAt);
        String executionId = Cmd.send(createCmd).getId();

        for (FlowNode node : dag.origins()) {
            execute(executionId, node.id());
        }

//        Map<String, String> refTaskIds = Cmd.send(new TaskBatchCreateCmd(
//            executionId, triggerAt, TaskRefType.FLOW_NODE,
//            dag.origins().stream().map(FlowNode::id).collect(Collectors.toList())
//        )).getRefTaskIds();
    }

    private void executeStartNode(StartNode node, String taskId) {
        // 目前没有业务逻辑，直接更新task为完成
    }
}
