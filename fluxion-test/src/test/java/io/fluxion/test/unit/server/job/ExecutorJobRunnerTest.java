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

package io.fluxion.test.unit.server.job;

import io.fluxion.server.core.job.runner.ExecutorJobRunner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExecutorJobRunner 单元测试
 *
 * 注意：完整测试需要Spring上下文，这里仅做基础验证
 */
public class ExecutorJobRunnerTest {

    @Test
    void testDispatchTargetCreation() {
        // Test the inner DispatchTarget class
        ExecutorJobRunner.DispatchTarget target = new ExecutorJobRunner.DispatchTarget("worker-1", 2);

        assertEquals("worker-1", target.getWorkerId());
        assertEquals(2, target.getAttempt());
    }

    @Test
    void testDispatchTarget_Equality() {
        // Two targets with same workerId and attempt
        ExecutorJobRunner.DispatchTarget target1 = new ExecutorJobRunner.DispatchTarget("worker-1", 2);
        ExecutorJobRunner.DispatchTarget target2 = new ExecutorJobRunner.DispatchTarget("worker-1", 2);

        assertEquals(target1.getWorkerId(), target2.getWorkerId());
        assertEquals(target1.getAttempt(), target2.getAttempt());
    }
}
