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

package io.fluxion.remote.core.cluster;

/**
 * @author Devil
 * @since 2022/7/18
 */
public class NodeEvent<T extends Node> {

    private final T node;

    private final Type type;

    public NodeEvent(T node, Type type) {
        this.node = node;
        this.type = type;
    }

    public T getNode() {
        return node;
    }

    public Type getType() {
        return type;
    }

    public enum Type {
        ONLINE,
        OFFLINE
    }
}
