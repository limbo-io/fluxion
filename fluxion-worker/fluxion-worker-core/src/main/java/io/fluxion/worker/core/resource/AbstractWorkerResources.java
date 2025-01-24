/*
 *
 *  * Copyright 2020-2024 fluxion Team (https://github.com/fluxion-io).
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * 	http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package io.fluxion.worker.core.resource;


import io.fluxion.worker.core.task.TaskQueue;

/**
 * @author Brozen
 * @since 2022-09-05
 */
public abstract class AbstractWorkerResources implements WorkerResources {

    /**
     * 并发执行任务数量
     */
    private int concurrency;

    /**
     * 任务队列
     */
    private final TaskQueue queue;

    public AbstractWorkerResources(int concurrency, int queueSize) {
        this.concurrency = concurrency;
        this.queue = new TaskQueue(queueSize);
    }

    /**
     * {@inheritDoc}
     *
     * @return
     */
    public int availableQueueSize() {
        return queue.capacity() - queue.size();
    }

    @Override
    public TaskQueue queue() {
        return queue;
    }

    @Override
    public int concurrency() {
        return concurrency;
    }

    @Override
    public int queueSize() {
        return queue.capacity();
    }
}
