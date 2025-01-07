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

package io.fluxion.platform.flow;

import io.fluxion.common.utils.dag.DAG;

import java.util.List;

/**
 * Flow 运行态
 *
 * @author Devil
 */
public class Flow {

    private String id;

    /**
     * 所有节点
     * 1. 入口必须为触发类型
     * 2. 普通节点的父节点可以是触发节点/普通节点
     * 3. 触发节点的字节点不能是触发节点
     */
    private DAG<FlowNode> dag;

    public FlowNode findNode(String nodeId) {
        return dag.node(nodeId);
    }

    public List<FlowNode> findSubNodes(String nodeId) {
        return dag.subNodes(nodeId);
    }

    public List<FlowNode> findPreNodes(String nodeId) {
        return dag.preNodes(nodeId);
    }

}
