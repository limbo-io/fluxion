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

package io.fluxion.server.core.cluster;

import io.fluxion.remote.core.cluster.Node;
import lombok.Getter;

/**
 * @author Devil
 * @since 2022/7/18
 */
@Getter
public class NodeEvent {

    private final Node node;

    private final Type type;

    public enum Type {
        ONLINE,
        OFFLINE
    }

    public NodeEvent(Node node, Type type) {
        this.node = node;
        this.type = type;
    }
}
