/*
 * Copyright 2025-2030 fluxion-io Team (https://github.com/limbo-io).
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

package io.fluxion.test.cluster.support;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * 有限期限轮询：不使用固定 sleep 作为通过依据，预算耗尽抛出携带阶段名的失败。
 */
public final class Await {

    private Await() {
    }

    public static void until(String stage, Duration budget, Duration interval, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + budget.toMillis();
        while (true) {
            if (condition.getAsBoolean()) {
                return;
            }
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("timeout waiting for: " + stage
                    + " (budget " + budget.toSeconds() + "s)");
            }
            try {
                Thread.sleep(interval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for: " + stage, e);
            }
        }
    }
}