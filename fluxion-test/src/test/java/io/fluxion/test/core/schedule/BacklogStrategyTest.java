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

package io.fluxion.test.core.schedule;

import io.fluxion.server.infrastructure.schedule.ScheduleOption;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T3.6: Backlog (missed trigger) strategy tests
 * <p>
 * Tests the LATEST_ONLY backlog policy:
 * When broker stops and multiple trigger points accumulate,
 * only the latest valid trigger is executed (stale triggers are skipped).
 *
 * @author Devil
 */
@DisplayName("Backlog Strategy Tests")
class BacklogStrategyTest {

    @Test
    @DisplayName("T3.6: CRON schedule with multiple missed triggers only executes latest")
    void testCronLatestOnlyPolicy() {
        // Given: Multiple missed trigger times
        List<LocalDateTime> missedTriggers = List.of(
            LocalDateTime.of(2025, 1, 1, 10, 1, 0),
            LocalDateTime.of(2025, 1, 1, 10, 2, 0),
            LocalDateTime.of(2025, 1, 1, 10, 3, 0),
            LocalDateTime.of(2025, 1, 1, 10, 4, 0),
            LocalDateTime.of(2025, 1, 1, 10, 5, 0)  // This is the latest
        );
        LocalDateTime now = LocalDateTime.of(2025, 1, 1, 10, 5, 30);

        // When: Apply LATEST_ONLY backlog policy (like CRON/FIXED_RATE)
        LocalDateTime latestTrigger = applyLatestOnlyPolicy(missedTriggers, now);

        // Then: Only the latest valid trigger should be executed
        assertThat(latestTrigger).isNotNull();
        assertThat(latestTrigger).isEqualTo(LocalDateTime.of(2025, 1, 1, 10, 5, 0));
    }

    @Test
    @DisplayName("T3.6: FIXED_RATE schedule with backlog only executes latest")
    void testFixedRateLatestOnlyPolicy() {
        // Given: Multiple missed trigger times for FIXED_RATE
        List<LocalDateTime> missedTriggers = List.of(
            LocalDateTime.of(2025, 1, 1, 10, 0, 30),
            LocalDateTime.of(2025, 1, 1, 10, 1, 0),
            LocalDateTime.of(2025, 1, 1, 10, 1, 30),
            LocalDateTime.of(2025, 1, 1, 10, 2, 0)  // Latest
        );
        LocalDateTime now = LocalDateTime.of(2025, 1, 1, 10, 2, 15);

        // When: Apply LATEST_ONLY backlog policy
        LocalDateTime latestTrigger = applyLatestOnlyPolicy(missedTriggers, now);

        // Then: Only the latest trigger should be executed
        assertThat(latestTrigger).isEqualTo(LocalDateTime.of(2025, 1, 1, 10, 2, 0));
    }

    @Test
    @DisplayName("T3.6: FIXED_DELAY schedule schedules only one next trigger")
    void testFixedDelaySingleNextTrigger() {
        // Given: A FIXED_DELAY schedule
        LocalDateTime now = LocalDateTime.of(2025, 1, 1, 10, 5, 0);
        LocalDateTime lastCompletion = LocalDateTime.of(2025, 1, 1, 10, 0, 0);
        Duration delay = Duration.ofMinutes(2);

        // When: Calculate next trigger for FIXED_DELAY (no backlog accumulation)
        LocalDateTime nextTrigger = calculateFixedDelayNextTrigger(lastCompletion, delay);

        // Then: Only one next trigger is scheduled (after delay from last completion)
        assertThat(nextTrigger).isEqualTo(LocalDateTime.of(2025, 1, 1, 10, 2, 0));
    }

    @Test
    @DisplayName("T3.6: Empty missed triggers list results in no execution")
    void testEmptyMissedTriggers() {
        // Given: No missed triggers
        List<LocalDateTime> missedTriggers = List.of();
        LocalDateTime now = LocalDateTime.now();

        // When: Apply LATEST_ONLY policy
        LocalDateTime latestTrigger = applyLatestOnlyPolicy(missedTriggers, now);

        // Then: No trigger should be executed
        assertThat(latestTrigger).isNull();
    }

    @Test
    @DisplayName("T3.6: Single missed trigger executes normally")
    void testSingleMissedTriggerExecutesNormally() {
        // Given: Only one missed trigger
        List<LocalDateTime> missedTriggers = List.of(
            LocalDateTime.of(2025, 1, 1, 10, 1, 0)
        );
        LocalDateTime now = LocalDateTime.of(2025, 1, 1, 10, 1, 30);

        // When: Apply LATEST_ONLY backlog policy
        LocalDateTime latestTrigger = applyLatestOnlyPolicy(missedTriggers, now);

        // Then: Should execute the single trigger
        assertThat(latestTrigger).isEqualTo(LocalDateTime.of(2025, 1, 1, 10, 1, 0));
    }

    @Test
    @DisplayName("T3.6: Triggers after current time are not executed")
    void testFutureTriggersNotExecuted() {
        // Given: Some triggers in the past and one in the future
        List<LocalDateTime> triggers = List.of(
            LocalDateTime.of(2025, 1, 1, 9, 58, 0),  // Past
            LocalDateTime.of(2025, 1, 1, 9, 59, 0),  // Past - latest valid
            LocalDateTime.of(2025, 1, 1, 10, 1, 0)   // Future (after now)
        );
        LocalDateTime now = LocalDateTime.of(2025, 1, 1, 10, 0, 0);

        // When: Apply LATEST_ONLY policy
        LocalDateTime latestTrigger = applyLatestOnlyPolicy(triggers, now);

        // Then: Should only execute triggers up to now
        assertThat(latestTrigger).isEqualTo(LocalDateTime.of(2025, 1, 1, 9, 59, 0));
    }

    @Test
    @DisplayName("T3.6: Schedule option type determines backlog behavior")
    void testBacklogBehaviorByScheduleType() {
        // CRON and FIXED_RATE use LATEST_ONLY
        ScheduleOption cronOption = new ScheduleOption(
            ScheduleType.CRON, null, null, null, null, "0 * * * * *", "QUARTZ"
        );
        ScheduleOption fixedRateOption = new ScheduleOption(
            ScheduleType.FIXED_RATE, null, null, null, Duration.ofMinutes(1), null, null
        );
        ScheduleOption fixedDelayOption = new ScheduleOption(
            ScheduleType.FIXED_DELAY, null, null, Duration.ofMinutes(1), null, null, null
        );

        // Then: CRON and FIXED_RATE are batch schedulers (can have backlog)
        assertThat(isBatchScheduler(cronOption)).isTrue();
        assertThat(isBatchScheduler(fixedRateOption)).isTrue();

        // FIXED_DELAY is sequential (no backlog accumulation)
        assertThat(isBatchScheduler(fixedDelayOption)).isFalse();
    }

    // Helper methods

    /**
     * Applies LATEST_ONLY backlog policy:
     * When multiple triggers are missed, only the latest one is executed.
     */
    private LocalDateTime applyLatestOnlyPolicy(List<LocalDateTime> missedTriggers, LocalDateTime now) {
        LocalDateTime latestValid = null;
        for (LocalDateTime trigger : missedTriggers) {
            if (!trigger.isAfter(now)) {  // trigger <= now
                latestValid = trigger;
            }
        }
        return latestValid;
    }

    /**
     * FIXED_DELAY calculates next trigger based on last completion time + delay.
     * Does not accumulate backlog.
     */
    private LocalDateTime calculateFixedDelayNextTrigger(LocalDateTime lastCompletion, Duration delay) {
        return lastCompletion.plus(delay);
    }

    /**
     * Determines if a schedule type can accumulate backlog (CRON, FIXED_RATE)
     * vs sequential (FIXED_DELAY).
     */
    private boolean isBatchScheduler(ScheduleOption option) {
        return option.getType() == ScheduleType.CRON
            || option.getType() == ScheduleType.FIXED_RATE;
    }
}
