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

package io.fluxion.test.integration.assertions;

import io.fluxion.remote.core.constants.JobStatus;
import io.fluxion.server.core.job.Job;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Job 执行断言工具
 *
 * @author Devil
 */
@Slf4j
public class JobExecutionAssert {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final long POLL_INTERVAL_MS = 100;

    /**
     * 断言 Job 在指定时间内完成（成功状态）
     *
     * @param jobId   job ID
     * @param timeout 超时时间
     */
    public static void assertJobCompleted(String jobId, Duration timeout) {
        assertJobStatus(jobId, JobStatus.SUCCEED::equals, timeout,
            "Job " + jobId + " should be completed");
    }

    /**
     * 断言 Job 在指定时间内失败
     *
     * @param jobId   job ID
     * @param timeout 超时时间
     */
    public static void assertJobFailed(String jobId, Duration timeout) {
        assertJobStatus(jobId, JobStatus.FAILED::equals, timeout,
            "Job " + jobId + " should be failed");
    }

    /**
     * 断言 Job 在指定时间内进入指定状态
     *
     * @param jobId    job ID
     * @param expected 期望状态
     * @param timeout  超时时间
     */
    public static void assertJobStatus(String jobId, JobStatus expected, Duration timeout) {
        assertJobStatus(jobId, status -> status == expected, timeout,
            "Job " + jobId + " should be in status " + expected);
    }

    /**
     * 断言 Job 在指定时间内满足条件
     *
     * @param jobId      job ID
     * @param predicate  状态判断条件
     * @param timeout    超时时间
     * @param message    失败消息
     */
    public static void assertJobStatus(String jobId, Predicate<JobStatus> predicate,
                                       Duration timeout, String message) {
        JobStatus finalStatus = waitForJobStatus(jobId, predicate, timeout);
        Assertions.assertTrue(predicate.test(finalStatus),
            message + ", but was " + finalStatus);
    }

    /**
     * 等待 Job 进入指定状态
     *
     * @param jobId      job ID
     * @param predicate  状态判断
     * @param timeout    超时时间
     * @return 最终状态
     */
    public static JobStatus waitForJobStatus(String jobId,
                                             Predicate<JobStatus> predicate,
                                             Duration timeout) {
        return waitForCondition(() -> getJobStatus(jobId), predicate, timeout);
    }

    /**
     * 等待条件满足
     *
     * @param supplier  状态提供者
     * @param predicate 条件判断
     * @param timeout   超时时间
     * @return 最终值
     */
    public static <T> T waitForCondition(Supplier<T> supplier,
                                          Predicate<T> predicate,
                                          Duration timeout) {
        long endTime = System.currentTimeMillis() + timeout.toMillis();

        T value = null;
        while (System.currentTimeMillis() < endTime) {
            value = supplier.get();
            if (predicate.test(value)) {
                return value;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for condition", e);
            }
        }

        return value;
    }

    /**
     * 断言 Job 被重试指定次数
     *
     * @param jobId             job ID
     * @param expectedRetryCount 期望重试次数
     * @param timeout           超时时间
     */
    public static void assertJobRetried(String jobId, int expectedRetryCount,
                                        Duration timeout) {
        Job job = waitForJob(() -> getJob(jobId),
            j -> j != null && j.getRetryTimes() == expectedRetryCount, timeout);

        Assertions.assertNotNull(job, "Job " + jobId + " should exist");
        Assertions.assertEquals(expectedRetryCount, job.getRetryTimes(),
            "Job " + jobId + " should have been retried " + expectedRetryCount + " times");
    }

    /**
     * 断言 Job 在指定次数内成功
     *
     * @param jobId       job ID
     * @param maxRetries  最大重试次数
     * @param timeout     超时时间
     */
    public static void assertJobSuccessWithinRetries(String jobId, int maxRetries,
                                                      Duration timeout) {
        Job job = waitForJob(() -> getJob(jobId),
            j -> j != null && JobStatus.SUCCEED.equals(j.getStatus()), timeout);

        Assertions.assertNotNull(job, "Job " + jobId + " should exist");
        Assertions.assertEquals(JobStatus.SUCCEED, job.getStatus(),
            "Job " + jobId + " should succeed");
        Assertions.assertTrue(job.getRetryTimes() <= maxRetries,
            "Job " + jobId + " should succeed within " + maxRetries + " retries");
    }

    /**
     * 等待 Job 对象满足条件
     */
    private static Job waitForJob(Supplier<Job> supplier, Predicate<Job> predicate,
                                   Duration timeout) {
        return waitForCondition(supplier, predicate, timeout);
    }

    /**
     * 获取 Job 状态
     * 注意：这里需要与实际的 JobRepository 集成
     */
    private static JobStatus getJobStatus(String jobId) {
        // TODO: 需要从 JobRepository 查询
        // 暂时返回 null，实际实现时需要替换
        return JobStatus.INITED;
    }

    /**
     * 获取 Job 对象
     * 注意：这里需要与实际的 JobRepository 集成
     */
    private static Job getJob(String jobId) {
        // TODO: 需要从 JobRepository 查询
        // 暂时返回 null，实际实现时需要替换
        return null;
    }

    /**
     * 使用 CountDownLatch 等待异步完成
     *
     * @param latch   计数器
     * @param timeout 超时时间
     */
    public static void awaitLatch(CountDownLatch latch, Duration timeout) {
        try {
            boolean completed = latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            Assertions.assertTrue(completed,
                "Operation did not complete within " + timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting", e);
        }
    }
}
