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

package io.fluxion.server.core.worker.dispatch;

import io.fluxion.server.infrastructure.tag.TagFilterOption;
import io.fluxion.server.core.worker.Worker;
import io.fluxion.server.core.worker.executor.WorkerExecutor;
import io.fluxion.server.core.worker.metric.WorkerMetric;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于配置过滤出合适的worker
 */
public class WorkerFilter {

    private List<Worker> workers;

    public WorkerFilter(List<Worker> workers) {
        this.workers = CollectionUtils.isEmpty(workers) ? Collections.emptyList() : workers;
    }

    /**
     * 基于执行器选择
     */
    public WorkerFilter filterExecutor(String executorName) {
        List<Worker> filterWorkers = workers.stream()
            .filter(worker -> {
                List<WorkerExecutor> executors = worker.getExecutors();
                if (CollectionUtils.isEmpty(executors)) {
                    return false;
                }
                // 判断是否有对应的执行器
                for (WorkerExecutor executor : executors) {
                    if (executor.getName().equals(executorName)) {
                        return true;
                    }
                }
                return false;
            })
            .collect(Collectors.toList());
        return new WorkerFilter(filterWorkers);
    }


    /**
     * 基于标签选择
     */
    public WorkerFilter filterTags(List<TagFilterOption> tagFilters) {
        List<Worker> filterWorkers = this.workers;
        if (CollectionUtils.isNotEmpty(tagFilters)) {
            for (TagFilterOption tagFilter : tagFilters) {
                filterWorkers = filterWorkers.stream().filter(tagFilter.asPredicate()).collect(Collectors.toList());
            }
        }
        return new WorkerFilter(filterWorkers);
    }

    /**
     * 基于资源过滤
     * @param maxCpuLoad 最大允许的CPU负载（百分比），worker的cpuLoad必须 <= maxCpuLoad
     * @param minFreeMemory 最小可用内存(MB)，worker的freeMemory必须 >= minFreeMemory
     */
    public WorkerFilter filterResources(Double maxCpuLoad, Long minFreeMemory) {
        List<Worker> filterWorkers = workers.stream().filter(worker -> {
            WorkerMetric metric = worker.getMetric();
            if (metric == null) {
                return false;
            }
            // 队列必须有可用空间
            if (metric.getAvailableQueueNum() <= 0) {
                return false;
            }
            // CPU负载必须 <= 最大允许值
            if (maxCpuLoad != null && maxCpuLoad > 0 && metric.getCpuLoad() > maxCpuLoad) {
                return false;
            }
            // 可用内存必须 >= 最小要求
            if (minFreeMemory != null && minFreeMemory > 0 && metric.getFreeMemory() < minFreeMemory) {
                return false;
            }
            return true;
        }).collect(Collectors.toList());
        return new WorkerFilter(filterWorkers);
    }

    /**
     * 获取worker
     */
    public List<Worker> get() {
        return workers;
    }

}
