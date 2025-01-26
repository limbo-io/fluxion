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
import io.fluxion.server.core.flow.node.FlowNode;
import io.fluxion.server.core.task.Task;
import io.fluxion.server.core.task.TaskStatus;
import io.fluxion.server.core.task.TaskType;
import io.fluxion.server.core.task.cmd.TasksCreateCmd;
import io.fluxion.server.core.task.cmd.TasksScheduleCmd;
import io.fluxion.server.core.task.query.TaskStatusByRefQuery;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.dag.DAG;
import io.fluxion.server.infrastructure.validata.ValidatableConfig;
import lombok.Getter;

import java.util.List;
import java.util.Map;
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

    /**
     * 执行某个节点
     */
    public void execute(String executionId, String nodeId) {
        // 判断前置节点执行状态，是否能执行当前节点
        List<FlowNode> parentNodes = dag.preNodes(nodeId);
        Map<String, TaskStatus> id2Status = Query.query(new TaskStatusByRefQuery()).getId2Status();


//        Cmd.send(new TaskRunCmd(executionId, TaskRefType.FLOW_NODE, nodeId));
        Task task = null; // todo @d
        if (task == null) {
            // todo @d create
        }


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
    public void execute(RunContext context) {
        List<Task> starNodeTasks = dag.origins().stream().map(n -> {
            Task task = new Task();
            task.setExecutionId(context.executionId());
            task.setRefId(n.id());
            task.setType(TaskType.INPUT_OUTPUT);
            return task;
        }).collect(Collectors.toList());

        Cmd.send(new TasksCreateCmd(starNodeTasks));
        Cmd.send(new TasksScheduleCmd(starNodeTasks, TimeUtils.currentLocalDateTime()));
    }

}
