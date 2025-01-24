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
import java.util.Objects;

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
        // 单个节点校验
        for (FlowNode node : nodes) {
            infos.addAll(node.validate());
        }
        if (CollectionUtils.isNotEmpty(infos)) {
            return infos;
        }
        // dag校验
        DAG<FlowNode> dag = new DAG<>(nodes, edges);
        // 开始节点
        if (CollectionUtils.isEmpty(dag.origins())) {
            infos.add(new ValidateSuppressInfo(FlowConstants.FLOW_START_NODES_IS_EMPTY));
            return infos;
        }
        if (dag.origins().stream().anyMatch(n -> !Objects.equals(n.getType(), FlowNode.Type.START))) {
            infos.add(new ValidateSuppressInfo(FlowConstants.FLOW_START_NODES_HAS_ERROR_TYPE_NODE));
            return infos;
        }
        // 结束节点
        if (CollectionUtils.isEmpty(dag.lasts())) {
            infos.add(new ValidateSuppressInfo(FlowConstants.FLOW_END_NODES_IS_EMPTY));
            return infos;
        }
        if (dag.lasts().stream().anyMatch(n -> !Objects.equals(n.getType(), FlowNode.Type.END))) {
            infos.add(new ValidateSuppressInfo(FlowConstants.FLOW_END_NODES_HAS_ERROR_TYPE_NODE));
            return infos;
        }
        // 是否成环
        if (dag.hasCyclic()) {
            infos.add(new ValidateSuppressInfo(FlowConstants.FLOW_HAS_CYCLIC));
            return infos;
        }
        return infos;
    }

}
