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

package io.fluxion.server.core.schedule;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 历史补建的吞吐保护（CONTEXT.md「Execution 预生成窗口」：按固定批次推进，绝不折叠丢弃）。
 * <p>
 * 单轮补建最多 {@value #BACKFILL_BATCH} 个触发点；批次耗尽时未生成点由持久化的
 * nextTriggerAt 游标承接，下一轮扫描续传。输入须按时间升序，截断总是保留最早的点，
 * 保证恢复期过后才补到近未来。
 * <p>
 * ponytail: 批次 100/轮；若单轮事务轻度仍超预期，先调此常量而非引入并发补建。
 */
public final class ScheduleBacklogPlanner {

    /** 单轮补建批次上限（对齐 ScheduleNextTriggerQuery 页大小） */
    public static final int BACKFILL_BATCH = 100;

    private ScheduleBacklogPlanner() {
    }

    public static Plan plan(List<LocalDateTime> triggerPoints, LocalDateTime now) {
        if (triggerPoints.size() <= BACKFILL_BATCH) {
            return new Plan(triggerPoints);
        }
        // 升序输入 → 保留最早的 BACKFILL_BATCH 个，剩余由 nextTriggerAt 游标下轮续传
        return new Plan(new ArrayList<>(triggerPoints.subList(0, BACKFILL_BATCH)));
    }

    @Getter
    @AllArgsConstructor
    public static class Plan {
        private final List<LocalDateTime> delays;
    }
}