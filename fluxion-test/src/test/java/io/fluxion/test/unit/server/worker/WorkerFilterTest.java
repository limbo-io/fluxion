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

import io.fluxion.server.core.worker.Worker;
import io.fluxion.server.core.worker.dispatch.WorkerFilter;
import io.fluxion.server.core.worker.executor.WorkerExecutor;
import io.fluxion.server.core.worker.metric.WorkerMetric;
import io.fluxion.server.infrastructure.tag.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WorkerFilter 单元测试
 */
public class WorkerFilterTest {

    @Test
    void testFilterResources_NullMetricHandling() {
        // Worker with null metric should be filtered out
        Worker worker = createWorker("worker-1", 50.0, 1024L, 5, true);
        worker = new Worker("worker-1", "app-1", "localhost", 8080,
            io.fluxion.remote.core.constants.Protocol.HTTP,
            Collections.singletonList(new WorkerExecutor("exec-1", null)),
            Collections.emptyList(), null, Worker.Status.ONLINE, true);

        WorkerFilter filter = new WorkerFilter(Collections.singletonList(worker));
        List<Worker> result = filter.filterResources(80.0, 512L).get();

        assertTrue(result.isEmpty(), "Worker with null metric should be filtered out");
    }

    @Test
    void testFilterResources_BoundaryEquality_CpuLoadEqualsMaxAllowed() {
        // cpuLoad == maxCpuLoad should be allowed
        Worker worker = createWorker("worker-1", 80.0, 1024L, 5, true);

        WorkerFilter filter = new WorkerFilter(Collections.singletonList(worker));
        List<Worker> result = filter.filterResources(80.0, 512L).get();

        assertEquals(1, result.size(), "Worker with cpuLoad == maxCpuLoad should pass");
    }

    @Test
    void testFilterResources_OverloadRejection() {
        // Worker CPU load exceeds max allowed should be filtered out
        Worker worker1 = createWorker("worker-1", 90.0, 1024L, 5, true); // Overloaded
        Worker worker2 = createWorker("worker-2", 70.0, 1024L, 5, true); // Normal

        WorkerFilter filter = new WorkerFilter(List.of(worker1, worker2));
        List<Worker> result = filter.filterResources(80.0, 512L).get();

        assertEquals(1, result.size(), "Only non-overloaded worker should pass");
        assertEquals("worker-2", result.get(0).id());
    }

    @Test
    void testFilterResources_QueueFullRejection() {
        // Worker with 0 available queue should be filtered out
        Worker worker1 = createWorker("worker-1", 50.0, 1024L, 0, true); // Queue full
        Worker worker2 = createWorker("worker-2", 50.0, 1024L, 5, true); // Has space

        WorkerFilter filter = new WorkerFilter(List.of(worker1, worker2));
        List<Worker> result = filter.filterResources(80.0, 512L).get();

        assertEquals(1, result.size(), "Only worker with available queue should pass");
        assertEquals("worker-2", result.get(0).id());
    }

    @Test
    void testFilterResources_MemoryInsufficient() {
        // Worker with insufficient free memory should be filtered out
        Worker worker1 = createWorker("worker-1", 50.0, 256L, 5, true);  // Low memory
        Worker worker2 = createWorker("worker-2", 50.0, 1024L, 5, true); // Enough memory

        WorkerFilter filter = new WorkerFilter(List.of(worker1, worker2));
        List<Worker> result = filter.filterResources(80.0, 512L).get();

        assertEquals(1, result.size(), "Only worker with sufficient memory should pass");
        assertEquals("worker-2", result.get(0).id());
    }

    @Test
    void testFilterResources_NoLimits() {
        // maxCpuLoad <= 0 and minFreeMemory <= 0 means no restriction
        Worker worker = createWorker("worker-1", 90.0, 100L, 5, true);

        WorkerFilter filter = new WorkerFilter(Collections.singletonList(worker));
        List<Worker> result = filter.filterResources(0.0, 0L).get();

        assertEquals(1, result.size(), "Worker should pass when no limits are set");
    }

    @Test
    void testFilterResources_MultipleConditionsCombined() {
        // Test multiple conditions working together
        Worker worker1 = createWorker("worker-1", 90.0, 1024L, 5, true);  // CPU too high
        Worker worker2 = createWorker("worker-2", 50.0, 256L, 5, true);   // Memory too low
        Worker worker3 = createWorker("worker-3", 50.0, 1024L, 0, true); // Queue full
        Worker worker4 = createWorker("worker-4", 50.0, 1024L, 5, true);  // All OK

        WorkerFilter filter = new WorkerFilter(List.of(worker1, worker2, worker3, worker4));
        List<Worker> result = filter.filterResources(80.0, 512L).get();

        assertEquals(1, result.size(), "Only worker meeting all conditions should pass");
        assertEquals("worker-4", result.get(0).id());
    }

    private Worker createWorker(String id, double cpuLoad, long freeMemory, int availableQueueNum, boolean enabled) {
        WorkerMetric metric = new WorkerMetric(4, cpuLoad, freeMemory, availableQueueNum,
            java.time.LocalDateTime.now());

        return new Worker(id, "app-1", "localhost", 8080,
            io.fluxion.remote.core.constants.Protocol.HTTP,
            Collections.singletonList(new WorkerExecutor("exec-1", null)),
            Collections.emptyList(), metric, Worker.Status.ONLINE, enabled);
    }
}
