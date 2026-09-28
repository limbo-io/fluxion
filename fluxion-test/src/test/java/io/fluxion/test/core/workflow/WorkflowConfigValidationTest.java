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

import io.fluxion.server.core.workflow.HandleEdge;
import io.fluxion.server.core.workflow.WorkflowConfig;
import io.fluxion.server.core.workflow.WorkflowConstants;
import io.fluxion.server.core.workflow.node.EndNode;
import io.fluxion.server.core.workflow.node.StartNode;
import io.fluxion.server.core.workflow.node.WorkflowNode;
import io.fluxion.server.infrastructure.validata.ValidateSuppressInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Workflow 配置领域校验不变量测试。
 * <p>
 * CONTEXT.md「Workflow 结束条件」：配置必须恰好一个 START 与一个 END。
 * 审查修复补齐 START 唯一性的校验执法：多根使首批 Job 不唯一，
 * 任一根因 Deadline 被拒即终结整 Execution，与仍在运行的其它根 Job 不一致。
 */
class WorkflowConfigValidationTest {

    @Test
    @DisplayName("V1: 恰好一个 START 起点：合法配置通过校验")
    void shouldPassWhenExactlyOneStartOrigin() {
        List<ValidateSuppressInfo> infos = validate(
            node(start("start-1"), end("end-1")),
            edge("start-1", "end-1")
        );
        assertThat(infos).isEmpty();
    }

    @Test
    @DisplayName("V2: 多个 START 根节点被拒绝：首批 Job 不唯一会破坏 Deadline 收敛语义")
    void shouldRejectMultipleStartOrigins() {
        List<ValidateSuppressInfo> infos = validate(
            node(start("start-1"), start("start-2"), end("end-1")),
            edge("start-1", "end-1", "start-2", "end-1")
        );
        assertThat(codes(infos)).contains(WorkflowConstants.FLOW_START_NODES_NOT_UNIQUE);
    }

    @Test
    @DisplayName("V3: 孤立 END 节点（无 START 起点）被既有规则拒绝")
    void shouldRejectMissingStartOrigin() {
        List<ValidateSuppressInfo> infos = validate(
            node(end("end-1")),
            edge()
        );
        assertThat(codes(infos)).contains(WorkflowConstants.FLOW_START_NODES_IS_EMPTY);
    }

    private List<ValidateSuppressInfo> validate(List<WorkflowNode> nodes, List<HandleEdge> edges) {
        WorkflowConfig config = new WorkflowConfig();
        config.setNodes(new ArrayList<>(nodes));
        config.setEdges(new ArrayList<>(edges));
        return config.validate();
    }

    private List<String> codes(List<ValidateSuppressInfo> infos) {
        return infos.stream().map(ValidateSuppressInfo::getCode).collect(Collectors.toList());
    }

    private List<WorkflowNode> node(WorkflowNode... nodes) {
        return List.of(nodes);
    }

    private List<HandleEdge> edge(String... pairs) {
        List<HandleEdge> result = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            HandleEdge edge = new HandleEdge();
            edge.setSourceNodeId(pairs[i]);
            edge.setTargetNodeId(pairs[i + 1]);
            result.add(edge);
        }
        return result;
    }

    private StartNode start(String id) {
        StartNode node = new StartNode();
        node.setId(id);
        node.setName(id);
        node.setType(WorkflowNode.Type.START);
        return node;
    }

    private EndNode end(String id) {
        EndNode node = new EndNode();
        node.setId(id);
        node.setName(id);
        node.setType(WorkflowNode.Type.END);
        return node;
    }
}