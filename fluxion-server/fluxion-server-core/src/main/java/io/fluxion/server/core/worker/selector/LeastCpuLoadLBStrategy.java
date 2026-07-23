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

package io.fluxion.server.core.worker.selector;

import io.fluxion.remote.core.lb.Invocation;
import io.fluxion.remote.core.lb.strategies.LBStrategy;
import io.fluxion.server.core.worker.Worker;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 最小CPU负载策略
 * 选择CPU负载最低的worker
 *
 * @author Devil
 */
public class LeastCpuLoadLBStrategy implements LBStrategy<Worker> {

    @Override
    public Worker select(List<Worker> workers, Invocation invocation) {
        if (workers == null || workers.isEmpty()) {
            return null;
        }

        // 按CPU负载升序排序，选择负载最低的
        return workers.stream()
            .sorted(Comparator.comparingDouble(this::getCpuLoad))
            .findFirst()
            .orElse(null);
    }

    /**
     * 按CPU负载对候选者排序（升序，负载低的在前）
     */
    public List<Worker> sort(List<Worker> workers) {
        if (workers == null || workers.isEmpty()) {
            return workers;
        }
        return workers.stream()
            .sorted(Comparator.comparingDouble(this::getCpuLoad))
            .collect(Collectors.toList());
    }

    /**
     * 获取worker的CPU负载，无指标时返回最大值
     */
    private double getCpuLoad(Worker worker) {
        if (worker.getMetric() == null) {
            return Double.MAX_VALUE;
        }
        return worker.getMetric().getCpuLoad();
    }
}
