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

import io.fluxion.server.core.flow.node.FlowNode;
import io.fluxion.server.infrastructure.dag.DAG;
import io.fluxion.server.infrastructure.validata.ValidatableConfig;
import io.fluxion.server.infrastructure.validata.ValidateSuppressInfo;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Flow 配置态
 *
 * @author Devil
 */
@Data
public class FlowConfig implements ValidatableConfig {

    /**
     * 所有节点
     */
    private List<FlowNode> nodes;

    /**
     * 连线
     */
    private List<HandleEdge> edges;

    /**
     * 校验信息
     */
    @Override
    public List<ValidateSuppressInfo> validate() {
        List<ValidateSuppressInfo> infos = new ArrayList<>();
        if (nodes.isEmpty()) {
            infos.add(new ValidateSuppressInfo(FlowConstants.FLOW_NODE_EMPTY));
            return infos;
        }
        if (nodes.size() > FlowConstants.FLOW_NODE_MAX_SIZE) {
            infos.add(new ValidateSuppressInfo(FlowConstants.FLOW_NODE_OVER_LIMIT));
            return infos;
        }
        // 开始节点
        if (nodes.stream().filter(n -> FlowNode.Type.START.equals(n.getType())).count() != 1) {
            infos.add(new ValidateSuppressInfo(FlowConstants.FLOW_NODE_START_END_LIMIT));
            return infos;
        }
        // 结束节点
        if (nodes.stream().filter(n -> FlowNode.Type.END.equals(n.getType())).count() != 1) {
            infos.add(new ValidateSuppressInfo(FlowConstants.FLOW_NODE_START_END_LIMIT));
            return infos;
        }
        // 单个节点校验
        for (FlowNode node : nodes) {
            infos.addAll(node.validate());
        }
        if (CollectionUtils.isNotEmpty(infos)) {
            return infos;
        }
        // dag校验
        DAG<FlowNode> dag = new DAG<>(nodes, edges);
        if (CollectionUtils.isEmpty(dag.origins())) {
            infos.add(new ValidateSuppressInfo(FlowConstants.FLOW_ROOT_NODES_IS_EMPTY));
            return infos;
        }
        if (CollectionUtils.isEmpty(dag.lasts())) {
            infos.add(new ValidateSuppressInfo(FlowConstants.FLOW_LEAF_NODES_IS_EMPTY));
            return infos;
        }
        if (dag.hasCyclic()) {
            infos.add(new ValidateSuppressInfo(FlowConstants.FLOW_HAS_CYCLIC));
            return infos;
        }
        return infos;
    }

}
