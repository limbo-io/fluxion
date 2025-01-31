/*
 * Copyright 2024-2030 fluxion-io Team (https://github.com/fluxion-io).
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

package io.fluxion.worker.core.task;

import io.fluxion.worker.core.tracker.TrackerBak;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Worker 中执行的任务队列
 *
 * @author Devil
 * @since 2021/7/24
 */
public class TaskQueue {

    /**
     * 可分配任务总数
     */
    private int queueSize;


    /**
     * 当前 Worker 的所有任务存储在此 Map 中
     */
    private final Map<String, TrackerBak> tasks = new ConcurrentHashMap<>();

    public TaskQueue(int queueSize) {
        this.queueSize = queueSize;
    }

    /**
     * 任务积压队列大小
     */
    public int queueSize() {
        return queueSize;
    }

    /**
     * 剩余可分配任务数
     */
    public int availableQueueSize() {
        return queueSize - tasks.size();
    }


    /**
     * 尝试新增任务到仓库中：如果已存在相同 taskId 的任务，则不添加新的任务，返回 false；如不存在，则添加成功，返回 true。
     *
     * @param context 任务执行上下文
     */
    public boolean save(TrackerBak context) {
        if (availableQueueSize() <= 0) {
            return false;
        }
        return tasks.putIfAbsent(context.task().taskId(), context) == null;
    }


    /**
     * 从仓库中移除任务
     */
    public void delete(String uid) {
        tasks.remove(uid);
    }

}
