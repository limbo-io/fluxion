/*
 * Copyright 2025-2030 fluxion-io Team (https://github.com/fluxion-io).
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
import java.util.List;

/**
 * Produces one persisted occurrence for every calculated trigger point.
 * Misfire policy is evaluated later by the Broker after the occurrence is loaded.
 *
 * @author Devil
 */
public final class ScheduleBacklogPlanner {

    private ScheduleBacklogPlanner() {
    }

    public static Plan plan(List<LocalDateTime> triggerPoints, LocalDateTime now) {
        return new Plan(triggerPoints);
    }

    @Getter
    @AllArgsConstructor
    public static class Plan {
        private final List<LocalDateTime> delays;
    }
}
