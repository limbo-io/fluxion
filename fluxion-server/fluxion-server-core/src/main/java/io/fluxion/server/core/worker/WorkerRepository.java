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

package io.fluxion.server.core.worker;

import java.util.List;

/**
 * @author Devil
 */
public interface WorkerRepository {

    /**
     * 新增一个worker
     *
     * @param worker worker节点
     */
    void save(Worker worker);

    /**
     * 根据id查询worker
     *
     * @param id workerId
     * @return worker节点
     */
    Worker get(String id);


    /**
     * 删除一个worker，软删除
     *
     * @param id 需要被移除的workerId
     */
    void delete(String id);

    List<Worker> allByApp(String appId);

}
