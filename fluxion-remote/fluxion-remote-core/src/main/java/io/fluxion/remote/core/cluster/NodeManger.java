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

import java.util.List;

/**
 * 节点管理器
 *
 * @author Devil
 * @since 2022/7/20
 */
public interface NodeManger<T extends Node> {

    /**
     * 节点上线
     */
    void online(T node);

    /**
     * 节点下线
     */
    void offline(T node);

    /**
     * 检查节点是否存活
     */
    boolean alive(String url);

    T get(String id);

    /**
     * 所有存活节点
     */
    List<T> allAlive();

    /**
     * 为某个资源选择一个节点
     *
     * @param id 资源id
     * @return broker信息
     */
    T elect(String id);

}