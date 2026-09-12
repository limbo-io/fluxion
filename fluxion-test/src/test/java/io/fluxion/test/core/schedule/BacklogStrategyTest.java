/*
 * Copyright 2025-2030 Fluxion Team (https://github.com/Fluxion-io).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package io.fluxion.test.core.schedule;

import io.fluxion.server.core.schedule.ScheduleBacklogPlanner;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BacklogStrategyTest {

    @Test
    void preservesEveryHistoricalAndFutureTriggerPoint() {
        LocalDateTime now = LocalDateTime.of(2025, 1, 1, 10, 0, 0);
        List<LocalDateTime> triggerPoints = List.of(
            now.minusMinutes(2), now.minusMinutes(1), now,
            now.plusMinutes(1), now.plusMinutes(2)
        );

        ScheduleBacklogPlanner.Plan plan = ScheduleBacklogPlanner.plan(triggerPoints, now);

        assertThat(plan.getDelays()).containsExactlyElementsOf(triggerPoints);
    }
}
