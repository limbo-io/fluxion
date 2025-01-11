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

package io.fluxion.core.worker.dispatch;

import io.fluxion.core.worker.Worker;
import io.fluxion.core.worker.executor.WorkerExecutor;
import io.fluxion.core.worker.metric.WorkerAvailableResource;
import io.fluxion.core.tag.TagFilterOption;
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
     */
    public WorkerFilter filterResources(Float cpuRequirement, Long ramRequirement) {
        List<Worker> filterWorkers = workers.stream().filter(worker -> {
            WorkerAvailableResource availableResource = worker.getMetric().getAvailableResource();
            if (availableResource.getAvailableQueueLimit() <= 0) {
                return false;
            }
            if (cpuRequirement != null && availableResource.getAvailableCpu() < cpuRequirement) {
                return false;
            }
            if (ramRequirement != null && availableResource.getAvailableRam() < ramRequirement) {
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
