/*
 * Copyright 2025-2030 fluxion-io Team (https://github.com/fluxion-io).
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

package io.fluxion.server.core.broker;

import io.fluxion.remote.core.cluster.NodeManger;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存中缓存的 broker节点信息
 *
 * @author Devil
 * @since 2022/7/20
 */
@Slf4j
@Component
public class BrokerManger implements NodeManger<BrokerNode> {

    private static final Map<String, BrokerNode> nodes = new ConcurrentHashMap<>();

    /**
     * 节点上线
     */
    public void online(BrokerNode node) {
        nodes.putIfAbsent(node.id(), node);
        if (log.isDebugEnabled()) {
            log.debug("[BrokerManger] online {}", nodes);
        }
    }

    /**
     * 节点下线
     */
    public void offline(BrokerNode node) {
        nodes.remove(node.id());
        if (log.isDebugEnabled()) {
            log.debug("[BrokerManger] offline {}", nodes);
        }
    }

    /**
     * 检查节点是否存活
     */
    public boolean alive(String url) {
        return nodes.containsKey(url);
    }

    @Override
    public BrokerNode get(String id) {
        return nodes.get(id);
    }

    /**
     * 所有存活节点
     */
    @Override
    public List<BrokerNode> allAlive() {
        if (nodes.isEmpty() && log.isDebugEnabled()) {
            log.debug("[BrokerManger] allAlive {}", nodes);
        }
        return new ArrayList<>(nodes.values());
    }

}
