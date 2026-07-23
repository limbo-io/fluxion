/*
 * Copyright 2025-2030 Limbo Team (https://github.com/limbo-io).
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

package io.fluxion.test.core.worker;

import io.fluxion.remote.core.constants.Protocol;
import io.fluxion.remote.core.lb.LoadBalanceType;
import io.fluxion.server.core.worker.Worker;
import io.fluxion.server.core.worker.dispatch.WorkerFilter;
import io.fluxion.server.core.worker.executor.WorkerExecutor;
import io.fluxion.server.core.worker.metric.WorkerMetric;
import io.fluxion.server.core.worker.selector.WorkerSelectInvocation;
import io.fluxion.server.core.worker.selector.WorkerSelector;
import io.fluxion.server.core.worker.selector.WorkerSelectorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T5.2: Worker CPU 低负载排序测试
 * <p>
 * 验收标准：
 * 1. 三个均合格 worker（CPU 0.2/0.4/0.6）稳定选择 0.2
 * 2. 超过硬阈值的 worker 仍被排除
 *
 * @author Devil
 */
@DisplayName("Worker CPU Load Selection Tests")
class WorkerCpuLoadSelectionTest {

    private WorkerSelectorFactory selectorFactory;

    @BeforeEach
    void setUp() {
        selectorFactory = new WorkerSelectorFactory();
    }

    @Test
    @DisplayName("T5.2: LEAST_CPU_LOAD 策略选择 CPU 负载最低的 worker")
    void testLeastCpuLoadSelection() {
        // Given: 三个均合格 worker（CPU 0.2/0.4/0.6）
        Worker lowLoadWorker = createWorkerWithCpuLoad("worker-0.2", 0.2);
        Worker mediumLoadWorker = createWorkerWithCpuLoad("worker-0.4", 0.4);
        Worker highLoadWorker = createWorkerWithCpuLoad("worker-0.6", 0.6);

        List<Worker> workers = Arrays.asList(mediumLoadWorker, highLoadWorker, lowLoadWorker);

        // When: 使用 LEAST_CPU_LOAD 策略选择
        WorkerSelector selector = selectorFactory.newSelector(LoadBalanceType.LEAST_CPU_LOAD);
        WorkerSelectInvocation invocation = new WorkerSelectInvocation("test-executor", null);
        Worker selected = selector.select(invocation, workers);

        // Then: 稳定选择 0.2（最低负载）
        assertThat(selected).isNotNull();
        assertThat(selected.id()).isEqualTo("worker-0.2");
    }

    @Test
    @DisplayName("T5.2: LEAST_CPU_LOAD 结合资源阈值过滤超阈值 worker")
    void testLeastCpuLoadWithThresholdFiltering() {
        // Given: 多个 worker，部分超过硬阈值（CPU > 0.5）
        Worker veryLowLoadWorker = createWorkerWithCpuLoad("worker-0.2", 0.2);
        Worker lowLoadWorker = createWorkerWithCpuLoad("worker-0.4", 0.4);
        Worker mediumLoadWorker = createWorkerWithCpuLoad("worker-0.6", 0.6); // 超过 0.5 阈值
        Worker highLoadWorker = createWorkerWithCpuLoad("worker-0.8", 0.8);   // 超过 0.5 阈值

        List<Worker> workers = Arrays.asList(
            highLoadWorker, mediumLoadWorker, lowLoadWorker, veryLowLoadWorker
        );

        // When: 先应用资源过滤（maxCpuLoad=0.5），再使用 LEAST_CPU_LOAD 选择
        WorkerFilter filter = new WorkerFilter(workers)
            .filterExecutor("test-executor")
            .filterResources(0.5, null);
        List<Worker> filteredWorkers = filter.get();

        WorkerSelector selector = selectorFactory.newSelector(LoadBalanceType.LEAST_CPU_LOAD);
        WorkerSelectInvocation invocation = new WorkerSelectInvocation("test-executor", null);
        Worker selected = selector.select(invocation, filteredWorkers);

        // Then: 超过阈值的被排除，在合格中选择最低负载
        assertThat(filteredWorkers).hasSize(2);
        assertThat(filteredWorkers.stream().map(Worker::id)).
            containsExactlyInAnyOrder("worker-0.2", "worker-0.4");
        assertThat(selected).isNotNull();
        assertThat(selected.id()).isEqualTo("worker-0.2");
    }

    @Test
    @DisplayName("T5.2: 相同 CPU 负载时选择任一 worker")
    void testEqualCpuLoadSelection() {
        // Given: 多个相同 CPU 负载的 worker
        Worker worker1 = createWorkerWithCpuLoad("worker-1", 0.3);
        Worker worker2 = createWorkerWithCpuLoad("worker-2", 0.3);
        Worker worker3 = createWorkerWithCpuLoad("worker-3", 0.3);

        List<Worker> workers = Arrays.asList(worker1, worker2, worker3);

        // When: 使用 LEAST_CPU_LOAD 策略选择
        WorkerSelector selector = selectorFactory.newSelector(LoadBalanceType.LEAST_CPU_LOAD);
        WorkerSelectInvocation invocation = new WorkerSelectInvocation("test-executor", null);
        Worker selected = selector.select(invocation, workers);

        // Then: 任选一个（都是最低负载）
        assertThat(selected).isNotNull();
        assertThat(selected.id()).isIn("worker-1", "worker-2", "worker-3");
    }

    @Test
    @DisplayName("T5.2: 无指标 worker 排在最后")
    void testWorkerWithoutMetricLast() {
        // Given: 一个 worker 无指标，一个有低负载指标
        Worker noMetricWorker = createWorkerWithoutMetric(" worker-no-metric");
        Worker lowLoadWorker = createWorkerWithCpuLoad("worker-0.3", 0.3);

        List<Worker> workers = Arrays.asList(noMetricWorker, lowLoadWorker);

        // When: 使用 LEAST_CPU_LOAD 策略选择
        WorkerSelector selector = selectorFactory.newSelector(LoadBalanceType.LEAST_CPU_LOAD);
        WorkerSelectInvocation invocation = new WorkerSelectInvocation("test-executor", null);
        Worker selected = selector.select(invocation, workers);

        // Then: 选择有指标的 worker（无指标的视为最大负载）
        assertThat(selected).isNotNull();
        assertThat(selected.id()).isEqualTo("worker-0.3");
    }

    @Test
    @DisplayName("T5.2: sortCandidates 按 CPU 负载升序排序")
    void testSortCandidatesByCpuLoad() {
        // Given: 多个 worker，不同 CPU 负载
        Worker workerLow = createWorkerWithCpuLoad("worker-0.1", 0.1);
        Worker workerMedium = createWorkerWithCpuLoad("worker-0.5", 0.5);
        Worker workerHigh = createWorkerWithCpuLoad("worker-0.9", 0.9);

        List<Worker> workers = Arrays.asList(workerHigh, workerLow, workerMedium);

        // When: 使用 sortCandidates 排序
        List<Worker> sorted = selectorFactory.sortCandidates(
            LoadBalanceType.LEAST_CPU_LOAD, "test-executor", workers
        );

        // Then: 按 CPU 负载升序排序
        assertThat(sorted).hasSize(3);
        assertThat(sorted.get(0).id()).isEqualTo("worker-0.1");
        assertThat(sorted.get(1).id()).isEqualTo("worker-0.5");
        assertThat(sorted.get(2).id()).isEqualTo("worker-0.9");
    }

    @Test
    @DisplayName("T5.2: 空列表返回 null/空")
    void testEmptyWorkerList() {
        // Given: 空 worker 列表
        List<Worker> emptyWorkers = Collections.emptyList();

        // When: 使用 LEAST_CPU_LOAD 策略选择
        WorkerSelector selector = selectorFactory.newSelector(LoadBalanceType.LEAST_CPU_LOAD);
        WorkerSelectInvocation invocation = new WorkerSelectInvocation("test-executor", null);
        Worker selected = selector.select(invocation, emptyWorkers);

        // Then: 返回 null
        assertThat(selected).isNull();
    }

    // Helper methods

    private Worker createWorkerWithCpuLoad(String workerId, double cpuLoad) {
        WorkerMetric metric = WorkerMetric.builder()
            .cpuProcessors(4)
            .cpuLoad(cpuLoad)
            .freeMemory(8000L)
            .availableQueueNum(10)
            .lastHeartbeatAt(LocalDateTime.now())
            .build();
        WorkerExecutor executor = WorkerExecutor.builder()
            .name("test-executor")
            .build();
        return new Worker(
            workerId, "test-app", "localhost", 8080, Protocol.HTTP,
            Collections.singletonList(executor), Collections.emptyList(),
            metric, Worker.Status.ONLINE, true
        );
    }

    private Worker createWorkerWithoutMetric(String workerId) {
        WorkerExecutor executor = WorkerExecutor.builder()
            .name("test-executor")
            .build();
        return new Worker(
            workerId, "test-app", "localhost", 8080, Protocol.HTTP,
            Collections.singletonList(executor), Collections.emptyList(),
            null, Worker.Status.ONLINE, true
        );
    }
}
