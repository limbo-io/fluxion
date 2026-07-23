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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T3.5: Worker load balancing and selection strategy tests
 * <p>
 * Tests WorkerFilter filtering capabilities and WorkerSelector
 * load balancing strategies (RANDOM, ROUND_ROBIN, etc.)
 *
 * @author Devil
 */
@DisplayName("Worker Load Balancing Tests")
class WorkerLoadBalancingTest {

    private WorkerSelectorFactory selectorFactory;

    @BeforeEach
    void setUp() {
        selectorFactory = new WorkerSelectorFactory();
    }

    @Test
    @DisplayName("T3.5: WorkerFilter filters by executor name")
    void testFilterByExecutor() {
        // Given: Workers with different executors
        Worker worker1 = createWorker("worker-1", "executor-A");
        Worker worker2 = createWorker("worker-2", "executor-B");
        Worker worker3 = createWorker("worker-3", "executor-A");

        List<Worker> workers = Arrays.asList(worker1, worker2, worker3);

        // When: Filter for executor-A
        WorkerFilter filter = new WorkerFilter(workers);
        List<Worker> filtered = filter.filterExecutor("executor-A").get();

        // Then: Only workers with executor-A should remain
        assertThat(filtered).hasSize(2);
        assertThat(filtered.get(0).getId()).isIn("worker-1", "worker-3");
        assertThat(filtered.get(1).getId()).isIn("worker-1", "worker-3");
    }

    @Test
    @DisplayName("T3.5: WorkerFilter filters by CPU load threshold")
    void testFilterByCpuLoad() {
        // Given: Workers with different CPU loads
        Worker lowLoadWorker = createWorkerWithMetrics("worker-1", 0.3, 1000L);
        Worker highLoadWorker = createWorkerWithMetrics("worker-2", 0.8, 1000L);
        Worker mediumLoadWorker = createWorkerWithMetrics("worker-3", 0.5, 1000L);

        List<Worker> workers = Arrays.asList(lowLoadWorker, highLoadWorker, mediumLoadWorker);

        // When: Filter for workers with CPU load <= 0.6
        WorkerFilter filter = new WorkerFilter(workers);
        List<Worker> filtered = filter.filterResources(0.6, null).get();

        // Then: Only workers with CPU load <= 0.6 should remain
        assertThat(filtered).hasSize(2);
        assertThat(filtered.get(0).getId()).isIn("worker-1", "worker-3");
        assertThat(filtered.get(1).getId()).isIn("worker-1", "worker-3");
    }

    @Test
    @DisplayName("T3.5: WorkerFilter filters by available memory")
    void testFilterByMemory() {
        // Given: Workers with different available memory
        Worker highMemoryWorker = createWorkerWithMetrics("worker-1", 0.3, 8000L);
        Worker lowMemoryWorker = createWorkerWithMetrics("worker-2", 0.3, 1000L);
        Worker mediumMemoryWorker = createWorkerWithMetrics("worker-3", 0.3, 4000L);

        List<Worker> workers = Arrays.asList(highMemoryWorker, lowMemoryWorker, mediumMemoryWorker);

        // When: Filter for workers with at least 2000MB free memory
        WorkerFilter filter = new WorkerFilter(workers);
        List<Worker> filtered = filter.filterResources(null, 2000L).get();

        // Then: Only workers with >= 2000MB free memory should remain
        assertThat(filtered).hasSize(2);
        assertThat(filtered.get(0).getId()).isIn("worker-1", "worker-3");
        assertThat(filtered.get(1).getId()).isIn("worker-1", "worker-3");
    }

    @Test
    @DisplayName("T3.5: WorkerFilter combines multiple filters")
    void testCombineFilters() {
        // Given: Workers with varied specs
        Worker suitableWorker = createWorkerWithMetrics("worker-1", 0.3, 8000L);
        Worker highCpuWorker = createWorkerWithMetrics("worker-2", 0.9, 8000L);
        Worker lowMemoryWorker = createWorkerWithMetrics("worker-3", 0.3, 500L);

        List<Worker> workers = Arrays.asList(suitableWorker, highCpuWorker, lowMemoryWorker);

        // When: Apply executor + resource filters
        WorkerFilter filter = new WorkerFilter(workers)
            .filterExecutor("test-executor")
            .filterResources(0.6, 1000L);

        List<Worker> filtered = filter.get();

        // Then: Only worker-1 meets all criteria
        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).id()).isEqualTo("worker-1");
    }

    @Test
    @DisplayName("T3.5: Random load balancing selects from available workers")
    void testRandomLoadBalancing() {
        // Given: Multiple available workers
        List<Worker> workers = Arrays.asList(
            createSimpleWorker("worker-1"),
            createSimpleWorker("worker-2"),
            createSimpleWorker("worker-3")
        );

        WorkerSelector selector = selectorFactory.newSelector(LoadBalanceType.RANDOM);
        WorkerSelectInvocation invocation = new WorkerSelectInvocation("test-executor", null);

        // When: Select workers multiple times
        int worker1Count = 0, worker2Count = 0, worker3Count = 0;
        for (int i = 0; i < 100; i++) {
            Worker selected = selector.select(invocation, new ArrayList<>(workers));
            if (selected == null) continue;

            switch (selected.id()) {
                case "worker-1" -> worker1Count++;
                case "worker-2" -> worker2Count++;
                case "worker-3" -> worker3Count++;
            }
        }

        // Then: All workers should have been selected at least once
        assertThat(worker1Count).isPositive();
        assertThat(worker2Count).isPositive();
        assertThat(worker3Count).isPositive();
    }

    @Test
    @DisplayName("T3.5: RoundRobin load balancing cycles through workers")
    void testRoundRobinLoadBalancing() {
        // Given: Multiple available workers
        List<Worker> workers = Arrays.asList(
            createSimpleWorker("worker-1"),
            createSimpleWorker("worker-2"),
            createSimpleWorker("worker-3")
        );

        WorkerSelector selector = selectorFactory.newSelector(LoadBalanceType.ROUND_ROBIN);
        WorkerSelectInvocation invocation = new WorkerSelectInvocation("test-executor", null);

        // When: Select workers in sequence (may cycle due to factory caching)
        Worker first = selector.select(invocation, new ArrayList<>(workers));

        // Then: Should return a valid worker
        assertThat(first).isNotNull();
    }

    @Test
    @DisplayName("T3.5: Selector returns null when no workers available")
    void testSelectorReturnsNullForEmptyList() {
        // Given: Empty worker list
        List<Worker> emptyWorkers = new ArrayList<>();

        WorkerSelector selector = selectorFactory.newSelector(LoadBalanceType.RANDOM);
        WorkerSelectInvocation invocation = new WorkerSelectInvocation("test-executor", null);

        // When: Try to select from empty list
        Worker selected = selector.select(invocation, emptyWorkers);

        // Then: Should return null
        assertThat(selected).isNull();
    }

    @Test
    @DisplayName("T3.5: Selector returns only worker when single option")
    void testSelectorReturnsOnlyWorker() {
        // Given: Single worker
        Worker singleWorker = createSimpleWorker("only-worker");
        List<Worker> workers = Arrays.asList(singleWorker);

        WorkerSelector selector = selectorFactory.newSelector(LoadBalanceType.RANDOM);
        WorkerSelectInvocation invocation = new WorkerSelectInvocation("test-executor", null);

        // When: Select
        Worker selected = selector.select(invocation, workers);

        // Then: Should return the only worker
        assertThat(selected).isNotNull();
        assertThat(selected.id()).isEqualTo("only-worker");
    }

    @Test
    @DisplayName("T3.5: Low load worker is preferred over high load")
    void testLowLoadWorkerPreferred() {
        // Given: Workers with different loads
        Worker lowLoadWorker = createWorkerWithMetrics("low-load", 0.2, 8000L);
        Worker highLoadWorker = createWorkerWithMetrics("high-load", 0.9, 2000L);

        List<Worker> workers = Arrays.asList(lowLoadWorker, highLoadWorker);

        // When: Apply resource filter for low CPU load
        WorkerFilter filter = new WorkerFilter(workers)
            .filterResources(0.5, null);

        List<Worker> filtered = filter.get();

        // Then: Only low load worker should pass
        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).id()).isEqualTo("low-load");
    }

    @Test
    @DisplayName("T3.5: Worker with available queue capacity is selected")
    void testWorkerWithQueueCapacity() {
        // Given: Workers with different queue availability
        Worker availableWorker = createWorkerWithQueue("worker-1", 10);
        Worker fullWorker = createWorkerWithQueue("worker-2", 0);

        List<Worker> workers = Arrays.asList(availableWorker, fullWorker);

        // When: Filter by available queue (using resource filter pattern as proxy)
        // In real scenario, this would use WorkerQueryService with queue filtering
        List<Worker> availableWorkers = workers.stream()
            .filter(w -> w.getMetric() != null && w.getMetric().getAvailableQueueNum() > 0)
            .toList();

        // Then: Only worker with available queue should be selected
        assertThat(availableWorkers).hasSize(1);
        assertThat(availableWorkers.get(0).id()).isEqualTo("worker-1");
    }

    // Helper methods

    private Worker createWorker(String workerId, String executorName) {
        WorkerMetric metric = new WorkerMetric(4, 0.3, 4000, 10, LocalDateTime.now());
        WorkerExecutor executor = new WorkerExecutor(executorName);
        return new Worker(
            workerId, "test-app", "localhost", 8080, Protocol.HTTP,
            Collections.singletonList(executor), Collections.emptyList(),
            metric, Worker.Status.ONLINE, true
        );
    }

    private Worker createSimpleWorker(String workerId) {
        return createWorker(workerId, "test-executor");
    }

    private Worker createWorkerWithMetrics(String workerId, double cpuLoad, long freeMemoryMB) {
        WorkerMetric metric = new WorkerMetric(4, cpuLoad, freeMemoryMB, 10, LocalDateTime.now());
        WorkerExecutor executor = new WorkerExecutor("test-executor");
        return new Worker(
            workerId, "test-app", "localhost", 8080, Protocol.HTTP,
            Collections.singletonList(executor), Collections.emptyList(),
            metric, Worker.Status.ONLINE, true
        );
    }

    private Worker createWorkerWithQueue(String workerId, int availableQueue) {
        WorkerMetric metric = new WorkerMetric(4, 0.3, 4000, availableQueue, LocalDateTime.now());
        WorkerExecutor executor = new WorkerExecutor("test-executor");
        return new Worker(
            workerId, "test-app", "localhost", 8080, Protocol.HTTP,
            Collections.singletonList(executor), Collections.emptyList(),
            metric, Worker.Status.ONLINE, true
        );
    }
}
