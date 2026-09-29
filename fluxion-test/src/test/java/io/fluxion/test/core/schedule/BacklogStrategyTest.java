/*
 * Copyright 2025-2030 Fluxion Team (https://github.com/Fluxion-io).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package io.fluxion.test.core.schedule;

import io.fluxion.server.core.schedule.ScheduleBacklogPlanner;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BacklogStrategyTest {

    @Test
    void preservesEveryHistoricalAndFutureTriggerPoint() {
        Instant now = java.time.LocalDateTime.of(2025, 1, 1, 10, 0, 0).toInstant(java.time.ZoneOffset.UTC);
        List<Instant> triggerPoints = List.of(
            now.minusSeconds(120), now.minusSeconds(60), now,
            now.plusSeconds(60), now.plusSeconds(120)
        );

        ScheduleBacklogPlanner.Plan plan = ScheduleBacklogPlanner.plan(triggerPoints, now);

        assertThat(plan.getDelays()).containsExactlyElementsOf(triggerPoints);
    }

    @Test
    void truncatesOversizedBackfillToEarliestBatchAndDefersTheRestToCursor() {
        // 超过单轮批次上限时只补最早一批，其余由持久化的 nextTriggerAt 游标下轮续传
        Instant base = java.time.LocalDateTime.of(2025, 1, 1, 10, 0, 0).toInstant(java.time.ZoneOffset.UTC);
        List<Instant> triggerPoints = new ArrayList<>();
        for (int i = 0; i < ScheduleBacklogPlanner.BACKFILL_BATCH + 9; i++) {
            triggerPoints.add(base.plusSeconds(i*60));
        }

        ScheduleBacklogPlanner.Plan plan = ScheduleBacklogPlanner.plan(triggerPoints, base);

        assertThat(plan.getDelays()).hasSize(ScheduleBacklogPlanner.BACKFILL_BATCH);
        // 升序输入：截断总是保留最早的点
        assertThat(plan.getDelays()).containsExactlyElementsOf(
            triggerPoints.subList(0, ScheduleBacklogPlanner.BACKFILL_BATCH));
    }
}
