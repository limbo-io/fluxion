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

package io.fluxion.server.core.flow.dag;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Devil
 * @since 2022/8/1
 */
@Slf4j
@ToString
public class DAG<T extends DAGNode> {

    /**
     * 当遍历的时候第二次进入某个节点，表示成环
     */
    public static final int STATUS_VISITED = 1;

    /**
     * 当一个节点 所有子节点都已经被遍历 而且没有环
     * 则它的后继判断也可以省略了
     * 因为如果要形成环，必定是会访问之前已访问的节点
     * 最简单的例子：有环列表
     */
    public static final int STATUS_FILTER = 2;

    /**
     * 起始节点
     */
    private List<T> origins;

    /**
     * 末尾节点
     */
    private List<T> lasts;

    /**
     * key - nodeId value - 前置节点id
     */
    private Map<String, Set<String>> preIdMap;

    /**
     * key - nodeId value - 后置节点id
     */
    private Map<String, Set<String>> subIdMap;

    /**
     * 节点映射关系
     */
    private Map<String, T> nodeMap;

    /**
     * @param nodeList 不能为空
     * @param edges    只有一个节点的时候为空
     */
    public DAG(List<T> nodeList, List<? extends Edge> edges) {
        if (CollectionUtils.isEmpty(nodeList)) {
            return;
        }

        this.nodeMap = new HashMap<>();
        this.origins = new ArrayList<>();
        this.lasts = new ArrayList<>();
        this.preIdMap = new HashMap<>();
        this.subIdMap = new HashMap<>();

        init(nodeList, edges);
    }


    /**
     * 根据作业列表，初始化 DAG 结构
     */
    private void init(List<T> nodeList, List<? extends Edge> edges) {
        // 数据初始化
        nodeList.forEach(node -> {
            if (StringUtils.isBlank(node.id())) {
                return;
            }
            nodeMap.put(node.id(), node);
        });
        edges.forEach(edge -> {
            if (StringUtils.isBlank(edge.getTargetNodeId()) || !nodeMap.containsKey(edge.getTargetNodeId())) {
                return;
            }
            if (StringUtils.isBlank(edge.getSourceNodeId()) || !nodeMap.containsKey(edge.getSourceNodeId())) {
                return;
            }
            if (!preIdMap.containsKey(edge.getTargetNodeId())) {
                preIdMap.put(edge.getTargetNodeId(), new HashSet<>());
            }
            preIdMap.get(edge.getTargetNodeId()).add(edge.getSourceNodeId());
            if (!subIdMap.containsKey(edge.getSourceNodeId())) {
                subIdMap.put(edge.getSourceNodeId(), new HashSet<>());
            }
            subIdMap.get(edge.getSourceNodeId()).add(edge.getTargetNodeId());
        });
        // 获取 根节点（没有其它节点指向的节点）叶子节点（没有子节点的）
        subIdMap.keySet().forEach(id -> {
            T node = nodeMap.get(id);
            if (node == null) {
                return;
            }
            if (!preIdMap.containsKey(id)) {
                origins.add(node);
            }
        });
        preIdMap.keySet().forEach(id -> {
            T node = nodeMap.get(id);
            if (node == null) {
                return;
            }
            if (!subIdMap.containsKey(id)) {
                lasts.add(node);
            }
        });
    }


    /**
     * 从 DAG 中查找是否存在指定的节点，存在则返回作业信息，不存在返回null。
     */
    public T node(String id) {
        return nodeMap.get(id);
    }


    /**
     * 获取叶子节点 也就是最后执行的节点
     */
    public List<T> lasts() {
        return lasts;
    }


    /**
     * 获取所有根节点
     */
    public List<T> origins() {
        return origins;
    }


    /**
     * 获取后续节点
     */
    public List<T> subNodes(String id) {
        Set<String> subIds = subIdMap.get(id);
        if (CollectionUtils.isEmpty(subIds)) {
            return Collections.emptyList();
        }
        return subIds.stream().map(subId -> nodeMap.get(subId)).collect(Collectors.toList());
    }


    /**
     * 获取前置节点
     */
    public List<T> preNodes(String id) {
        Set<String> preIds = preIdMap.get(id);
        if (CollectionUtils.isEmpty(preIds)) {
            return Collections.emptyList();
        }
        return preIds.stream().map(preId -> nodeMap.get(preId)).collect(Collectors.toList());
    }

    /**
     * 判断是否有环
     */
    public boolean hasCyclic() {
        Map<String, Integer> nodeStatuesMap = new HashMap<>();
        for (T origin : origins()) {
            if (hasCyclic(origin, nodeStatuesMap)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 深度优先搜索
     *
     * @param node 搜索出发节点
     * @return 如果有环返回true
     */
    private boolean hasCyclic(T node, Map<String, Integer> nodeStatuesMap) {
        // 表示当前节点已被标记
        nodeStatuesMap.put(node.id(), STATUS_VISITED);
        // 如果不存在子节点 则表示此顶点不再有出度 返回父节点
        Set<String> subNodeIds = subIdMap.get(node.id());
        if (CollectionUtils.isNotEmpty(subNodeIds)) {
            // 遍历子节点
            for (String subId : subNodeIds) {
                T child = nodeMap.get(subId);
                if (child == null || Objects.equals(STATUS_FILTER, nodeStatuesMap.get(subId))) {
                    continue;
                }
                if (Objects.equals(STATUS_VISITED, nodeStatuesMap.get(subId))) {
                    return true;
                }
                if (hasCyclic(child, nodeStatuesMap)) {
                    return true;
                }
            }
        }
        nodeStatuesMap.put(node.id(), STATUS_FILTER);
        return false;
    }

}
