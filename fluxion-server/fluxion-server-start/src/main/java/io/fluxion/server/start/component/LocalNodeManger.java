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

package io.fluxion.server.start.component;

import io.fluxion.core.cluster.Node;
import io.fluxion.core.cluster.NodeManger;
import io.fluxion.common.utils.json.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 内存中缓存的 broker节点信息
 *
 * @author Devil
 * @since 2022/7/20
 */
@Slf4j
@Component
public class LocalNodeManger implements NodeManger {

    private static final Map<String, Node> nodes = new ConcurrentHashMap<>();

    /**
     * 节点上线
     */
    public void online(Node node) {
        URL url = node.getUrl();
        nodes.putIfAbsent(url.toString(), node);
        if (log.isDebugEnabled()) {
            log.debug("[LocalNodeManger] online {}", JacksonUtils.toJSONString(nodes));
        }
    }

    /**
     * 节点下线
     */
    public void offline(Node node) {
        URL url = node.getUrl();
        nodes.remove(url.toString());
        if (log.isDebugEnabled()) {
            log.debug("[LocalNodeManger] offline {}", JacksonUtils.toJSONString(nodes));
        }
    }

    /**
     * 检查节点是否存活
     */
    public boolean alive(String url) {
        return nodes.containsKey(url);
    }

    /**
     * 所有存活节点
     */
    public Collection<Node> allAlive() {
        if (nodes.isEmpty() && log.isDebugEnabled()) {
            log.debug("[LocalNodeManger] allAlive {}", JacksonUtils.toJSONString(nodes));
        }
        return nodes.values();
    }

    /**
     * 为某个资源选择一个broker
     *
     * @param id 资源id
     * @return broker信息
     */
    public Node elect(String id) {
        List<Node> sortedNodes = nodes.values().stream().sorted(Comparator
                .comparing((Function<Node, String>) node -> node.getUrl().getHost())
                .thenComparingInt(node -> node.getUrl().getPort())
        ).collect(Collectors.toList());

        if (CollectionUtils.isEmpty(sortedNodes)) {
            return null;
        }

        // hash获取id对应的值
        int idx = id.hashCode() % sortedNodes.size();

        Node node = sortedNodes.get(idx);
        log.info("find elect:{}", node);
        return node;
    }

}
