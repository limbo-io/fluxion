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

package io.fluxion.test.unit.server.worker;

import io.fluxion.remote.core.lb.LoadBalanceType;
import io.fluxion.server.core.worker.Worker;
import io.fluxion.server.core.worker.executor.WorkerExecutor;
import io.fluxion.server.core.worker.metric.WorkerMetric;
import io.fluxion.server.core.worker.selector.WorkerSelectorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WorkerSelector 单元测试 - 验证各负载均衡策略的行为
 */
public class WorkerSelectorTest {

    private WorkerSelectorFactory factory;

    @BeforeEach
    void setUp() {
        factory = new WorkerSelectorFactory();
    }

    @Test
    void testRoundRobinAlternatesBetweenTwoWorkers() {
        // Arrange
        Worker worker1 = createWorker("worker-1");
        Worker worker2 = createWorker("worker-2");
        List<Worker> workers = List.of(worker1, worker2);

        var selector = factory.getOrCreateSelector("app-1", "executor-1", LoadBalanceType.ROUND_ROBIN);

        // Act - 4 selections should alternate between worker1 and worker2
        List<String> selectedIds = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            var invocation = new io.fluxion.server.core.worker.selector.WorkerSelectInvocation("executor-1", null);
            Worker selected = selector.select(invocation, workers);
            selectedIds.add(selected.id());
        }

        // Assert
        assertEquals(4, selectedIds.size());
        // Should alternate: worker-1, worker-2, worker-1, worker-2
        // or: worker-2, worker-1, worker-2, worker-1 (depending on initial index)
        String first = selectedIds.get(0);
        String second = selectedIds.get(1);
        assertNotEquals(first, second, "First two selections should be different");
        assertEquals(first, selectedIds.get(2), "Third selection should match first");
        assertEquals(second, selectedIds.get(3), "Fourth selection should match second");
    }

    @Test
    void testLFU_SelectsWorkerWithFewerDispatches() {
        // Arrange
        Worker worker1 = createWorker("worker-1");
        Worker worker2 = createWorker("worker-2");
        List<Worker> workers = List.of(worker1, worker2);

        // Record 5 dispatches for worker1, 2 for worker2
        var statsRepo = factory.getStatisticsRepository();
        for (int i = 0; i < 5; i++) {
            statsRepo.recordDispatch(worker1);
        }
        for (int i = 0; i < 2; i++) {
            statsRepo.recordDispatch(worker2);
        }

        // Act
        List<Worker> candidates = factory.sortCandidates(LoadBalanceType.LEAST_FREQUENTLY_USED, "executor-1", workers);

        // Assert - LFU means fewer dispatches = better (first in sorted list)
        assertEquals("worker-2", candidates.get(0).id(),
            "LFU should prefer worker with fewer dispatches");
    }

    @Test
    void testLRU_SelectsWorkerNotDispatchedToLongest() {
        // Arrange
        Worker worker1 = createWorker("worker-1");
        Worker worker2 = createWorker("worker-2");
        Worker worker3 = createWorker("worker-3");
        List<Worker> workers = List.of(worker1, worker2, worker3);

        var statsRepo = factory.getStatisticsRepository();

        // Record dispatches in sequence: worker1 (oldest), worker2 (middle), worker3 (newest)
        // Wait a tiny bit to ensure different timestamps
        statsRepo.recordDispatch(worker1);
        try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        statsRepo.recordDispatch(worker2);
        try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        statsRepo.recordDispatch(worker3);

        // Act
        List<Worker> candidates = factory.sortCandidates(LoadBalanceType.LEAST_RECENTLY_USED, "executor-1", workers);

        // Assert - LRU means oldest access = better (first in sorted list)
        assertEquals("worker-1", candidates.get(0).id(),
            "LRU should prefer worker not dispatched to longest");
    }

    @Test
    void testSelectorInstanceReuse() {
        // Selector instances for the same (appId, executorName, type) should be reused
        var selector1 = factory.getOrCreateSelector("app-1", "executor-1", LoadBalanceType.ROUND_ROBIN);
        var selector2 = factory.getOrCreateSelector("app-1", "executor-1", LoadBalanceType.ROUND_ROBIN);

        assertSame(selector1, selector2, "Same key should return same selector instance");

        // Different appId should create new instance
        var selector3 = factory.getOrCreateSelector("app-2", "executor-1", LoadBalanceType.ROUND_ROBIN);
        assertNotSame(selector1, selector3, "Different key should return different instance");
    }

    @Test
    void testStatisticsRecordedOnDispatch() {
        Worker worker = createWorker("worker-1");
        var statsRepo = factory.getStatisticsRepository();

        // Initially should have 0 dispatches
        var stats = statsRepo.getStatistics(Collections.singleton("worker-1"), java.time.Duration.ofMinutes(10));
        if (!stats.isEmpty()) {
            assertEquals(0, stats.get(0).accessTimes(), "Initially should have 0 dispatches");
        }

        // Record dispatch
        statsRepo.recordDispatch(worker);

        // Should now have 1 dispatch
        stats = statsRepo.getStatistics(Collections.singleton("worker-1"), java.time.Duration.ofMinutes(10));
        assertFalse(stats.isEmpty(), "Should have statistics after dispatch");
        assertEquals(1, stats.get(0).accessTimes(), "Should have 1 dispatch recorded");
    }

    private Worker createWorker(String id) {
        WorkerMetric metric = new WorkerMetric(4, 50.0, 1024L, 10, java.time.LocalDateTime.now());
        return new Worker(id, "app-1", "localhost", 8080,
            io.fluxion.remote.core.constants.Protocol.HTTP,
            Collections.singletonList(new WorkerExecutor("exec-1", null)),
            Collections.emptyList(), metric, Worker.Status.ONLINE, true);
    }
}
