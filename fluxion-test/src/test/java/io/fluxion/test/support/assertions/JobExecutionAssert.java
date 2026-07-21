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

package io.fluxion.test.support.assertions;

import io.fluxion.remote.core.constants.JobStatus;
import io.fluxion.server.core.job.Job;
import io.fluxion.test.support.environment.TestProfiles;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 【测试基础设施 - Job 执行断言工具】
 * 
 * 作用：提供针对异步 Job 执行的高级断言方法
 *       解决异步任务测试中"何时断言"的问题
 * 
 * 核心功能：
 *   - 轮询等待指定状态（带超时）
 *   - 断言最终状态
 *   - 断言重试次数
 *   - CountDownLatch 等待
 * 
 * 使用示例：
 * <pre>
 *   // 断言 Job 在 30 秒内成功完成
 *   JobExecutionAssert.assertJobCompleted(jobId, Duration.ofSeconds(30));
 *   
 *   // 断言 Job 在 10 秒内失败
 *   JobExecutionAssert.assertJobFailed(jobId, Duration.ofSeconds(10));
 *   
 *   // 断言 Job 被重试 3 次
 *   JobExecutionAssert.assertJobRetried(jobId, 3, Duration.ofSeconds(30));
 * </pre>
 * 
 * 注意：getJobStatus/getJob 方法需要与实际 Repository 集成才能正常工作
 * 
 * @author Devil
 */
@Slf4j
public class JobExecutionAssert {

    /**
     * 默认断言超时时间
     */
    private static final Duration DEFAULT_TIMEOUT = TestProfiles.TIMEOUT_DEFAULT;
    
    /**
     * 轮询间隔（毫秒）
     */
    private static final long POLL_INTERVAL_MS = TestProfiles.POLL_INTERVAL.toMillis();

    /**
     * 断言 Job 在指定时间内成功完成
     * 
     * @param jobId   Job ID
     * @param timeout 最大等待时间
     * @throws AssertionError 超时或最终状态不是 SUCCEED
     */
    public static void assertJobCompleted(String jobId, Duration timeout) {
        assertJobStatus(jobId, JobStatus.SUCCEED::equals, timeout,
            "Job " + jobId + " 应该在指定时间内完成");
    }

    /**
     * 断言 Job 在指定时间内失败
     * 
     * @param jobId   Job ID
     * @param timeout 最大等待时间
     * @throws AssertionError 超时或最终状态不是 FAILED
     */
    public static void assertJobFailed(String jobId, Duration timeout) {
        assertJobStatus(jobId, JobStatus.FAILED::equals, timeout,
            "Job " + jobId + " 应该在指定时间内失败");
    }

    /**
     * 断言 Job 在指定时间内进入期望状态
     * 
     * @param jobId    Job ID
     * @param expected 期望状态
     * @param timeout  最大等待时间
     * @throws AssertionError 超时或状态不匹配
     */
    public static void assertJobStatus(String jobId, JobStatus expected, Duration timeout) {
        assertJobStatus(jobId, status -> status == expected, timeout,
            "Job " + jobId + " 应该进入状态 " + expected);
    }

    /**
     * 断言 Job 在指定时间内满足自定义条件
     * 
     * @param jobId     Job ID
     * @param predicate 状态判断条件
     * @param timeout   最大等待时间
     * @param message   断言失败时的消息
     * @throws AssertionError 超时或条件不满足
     */
    public static void assertJobStatus(String jobId, Predicate<JobStatus> predicate,
                                       Duration timeout, String message) {
        JobStatus finalStatus = waitForJobStatus(jobId, predicate, timeout);
        Assertions.assertTrue(predicate.test(finalStatus),
            message + ", 实际状态: " + finalStatus);
    }

    /**
     * 等待 Job 进入指定状态
     * 
     * @param jobId     Job ID
     * @param predicate 目标状态判断
     * @param timeout   最大等待时间
     * @return 最终状态（可能不满足条件，需调用方验证）
     */
    public static JobStatus waitForJobStatus(String jobId,
                                             Predicate<JobStatus> predicate,
                                             Duration timeout) {
        return waitForCondition(() -> getJobStatus(jobId), predicate, timeout);
    }

    /**
     * 通用条件等待方法
     * 
     * 轮询策略：
     *   1. 获取当前值
     *   2. 判断是否满足条件
     *   3. 满足则返回
     *   4. 不满足则等待 POLL_INTERVAL_MS 后重试
     *   5. 超时返回最后一次获取的值
     * 
     * @param supplier  值获取函数
     * @param predicate 条件判断
     * @param timeout   超时时间
     * @return 最终获取的值
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
                throw new RuntimeException("等待条件时线程被中断", e);
            }
        }

        return value;
    }

    /**
     * 断言 Job 被重试指定次数
     * 
     * 注意：Job.getRetryTimes() 返回的是重试次数，不是总执行次数
     * 总执行次数 = 重试次数 + 1（首次执行）
     * 
     * @param jobId              Job ID
     * @param expectedRetryCount 期望重试次数
     * @param timeout            最大等待时间
     * @throws AssertionError 重试次数不匹配
     */
    public static void assertJobRetried(String jobId, int expectedRetryCount,
                                        Duration timeout) {
        Job job = waitForJob(() -> getJob(jobId),
            j -> j != null && j.getRetryTimes() == expectedRetryCount, timeout);

        Assertions.assertNotNull(job, "Job " + jobId + " 应该存在");
        Assertions.assertEquals(expectedRetryCount, job.getRetryTimes(),
            "Job " + jobId + " 应该被重试 " + expectedRetryCount + " 次");
    }

    /**
     * 断言 Job 在指定重试次数内成功
     * 
     * @param jobId       Job ID
     * @param maxRetries  最大允许重试次数
     * @param timeout     最大等待时间
     * @throws AssertionError 未成功或重试次数超过限制
     */
    public static void assertJobSuccessWithinRetries(String jobId, int maxRetries,
                                                      Duration timeout) {
        Job job = waitForJob(() -> getJob(jobId),
            j -> j != null && JobStatus.SUCCEED.equals(j.getStatus()), timeout);

        Assertions.assertNotNull(job, "Job " + jobId + " 应该存在");
        Assertions.assertEquals(JobStatus.SUCCEED, job.getStatus(),
            "Job " + jobId + " 应该执行成功");
        Assertions.assertTrue(job.getRetryTimes() <= maxRetries,
            "Job " + jobId + " 应该在 " + maxRetries + " 次重试内成功");
    }

    /**
     * 等待 Job 对象满足条件
     */
    private static Job waitForJob(Supplier<Job> supplier, Predicate<Job> predicate,
                                   Duration timeout) {
        return waitForCondition(supplier, predicate, timeout);
    }

    /**
     * 获取 Job 状态（需与 Repository 集成）
     * 
     * ⚠️ TODO: 当前为占位实现，需要替换为实际的 Repository 查询
     */
    private static JobStatus getJobStatus(String jobId) {
        // TODO: 集成 JobRepository 实现真实查询
        // return jobRepository.findById(jobId).map(Job::getStatus).orElse(null);
        log.warn("[JobExecutionAssert] getJobStatus 未实现真实查询，返回默认值");
        return JobStatus.INITED;
    }

    /**
     * 获取 Job 对象（需与 Repository 集成）
     * 
     * ⚠️ TODO: 当前为占位实现，需要替换为实际的 Repository 查询
     */
    private static Job getJob(String jobId) {
        // TODO: 集成 JobRepository 实现真实查询
        // return jobRepository.findById(jobId).orElse(null);
        log.warn("[JobExecutionAssert] getJob 未实现真实查询，返回 null");
        return null;
    }

    /**
     * 等待 CountDownLatch 倒计时完成
     * 
     * 使用场景：测试广播任务（需等待多个 Worker 执行完成）
     * 
     * @param latch   计数器
     * @param timeout 最大等待时间
     * @throws AssertionError 超时未完成
     */
    public static void awaitLatch(CountDownLatch latch, Duration timeout) {
        try {
            boolean completed = latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            Assertions.assertTrue(completed,
                "操作未在 " + timeout + " 内完成");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("等待时线程被中断", e);
        }
    }
}
