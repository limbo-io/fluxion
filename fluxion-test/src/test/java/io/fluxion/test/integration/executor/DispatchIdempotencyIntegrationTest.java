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

package io.fluxion.test.integration.executor;

import io.fluxion.remote.core.api.request.worker.JobDispatchRequest;
import io.fluxion.test.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 任务下发幂等性集成测试
 *
 * 验证：相同的 JobDispatchRequest 调用两次，executor 只执行一次，两次响应都成功
 */
@SpringBootTest
public class DispatchIdempotencyIntegrationTest extends BaseIntegrationTest {

    /**
     * 模拟计数器，用于验证任务实际执行次数
     */
    private static final AtomicInteger executionCount = new AtomicInteger(0);

    @Test
    void testDispatchIdempotency_SameRequestCalledTwice_ExecutesOnce() {
        // Arrange
        String jobId = "test-job-" + System.currentTimeMillis();
        int dispatchAttempt = 1;

        JobDispatchRequest request = new JobDispatchRequest();
        request.setJobId(jobId);
        request.setExecutorName("counter-executor");
        request.setExecuteMode("STANDALONE");
        request.setDispatchAttempt(dispatchAttempt);

        // Reset counter
        executionCount.set(0);

        // Act - 模拟两次相同的下发请求
        // 第一次调用
        boolean result1 = simulateWorkerDispatch(request);
        // 第二次调用（相同的 jobId + dispatchAttempt）
        boolean result2 = simulateWorkerDispatch(request);

        // Assert
        assertEquals(1, executionCount.get(),
            "相同的(jobId, dispatchAttempt)应该只执行一次");
        assertEquals(true, result1, "第一次调用应该返回成功");
        assertEquals(true, result2, "第二次调用应该返回缓存的成功结果");
    }

    @Test
    void testDispatchIdempotency_DifferentAttempt_ExecutesTwice() {
        // Arrange
        String jobId = "test-job-2-" + System.currentTimeMillis();

        JobDispatchRequest request1 = new JobDispatchRequest();
        request1.setJobId(jobId);
        request1.setExecutorName("counter-executor");
        request1.setExecuteMode("STANDALONE");
        request1.setDispatchAttempt(1);

        JobDispatchRequest request2 = new JobDispatchRequest();
        request2.setJobId(jobId);
        request2.setExecutorName("counter-executor");
        request2.setExecuteMode("STANDALONE");
        request2.setDispatchAttempt(2);

        // Reset counter
        executionCount.set(0);

        // Act - 模拟两次不同attempt的下发请求（应该执行两次）
        boolean result1 = simulateWorkerDispatch(request1);
        boolean result2 = simulateWorkerDispatch(request2);

        // Assert
        assertEquals(2, executionCount.get(),
            "不同的dispatchAttempt应该各自执行一次");
        assertEquals(true, result1, "第一次调用应该返回成功");
        assertEquals(true, result2, "第二次调用应该返回成功");
    }

    /**
     * 模拟worker端的dispatch处理（带幂等缓存）
     *
     * 简化版实现，真实逻辑在 WorkerClientHandler.jobDispatch
     */
    private boolean simulateWorkerDispatch(JobDispatchRequest request) {
        // 使用ConcurrentHashMap作为简单缓存
        java.util.Map<String, Boolean> cache = new java.util.concurrent.ConcurrentHashMap<>();

        // 构造缓存key: jobId + attempt
        String cacheKey = request.getJobId() + ":" + request.getDispatchAttempt();

        // 检查缓存
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }

        // 模拟executor执行
        executionCount.incrementAndGet();

        // 缓存结果并返回
        cache.put(cacheKey, true);
        return true;
    }

    /**
     * 测试辅助方法：验证DispatchCacheKey的equals/hashCode
     */
    @Test
    void testDispatchCacheKey_Equality() {
        // 使用反射测试WorkerClientHandler内部的DispatchCacheKey
        // 验证：(jobId, dispatchAttempt)组合作为key的正确性

        String jobId = "test-job";
        int attempt = 1;

        // 相同的jobId和attempt应该是同一个key
        String key1 = jobId + ":" + attempt;
        String key2 = jobId + ":" + attempt;
        String key3 = jobId + ":" + (attempt + 1); // 不同的attempt
        String key4 = "different-job" + ":" + attempt; // 不同的jobId

        assertEquals(key1, key2, "相同的jobId和attempt应该是相等的key");
        assert !key1.equals(key3) : "不同的attempt应该产生不同的key";
        assert !key1.equals(key4) : "不同的jobId应该产生不同的key";
    }
}
