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

package io.fluxion.platform.schedule.calculator;


import com.google.common.collect.Lists;
import io.fluxion.platform.schedule.ScheduleType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Brozen
 * @since 2021-05-20
 */
public class ScheduleCalculatorFactory {

    /**
     * 全部策略
     */
    private static final Map<ScheduleType, ScheduleCalculator> scheduleCalculators = new EnumMap<>(ScheduleType.class);

    static {
        List<ScheduleCalculator> calculators = Lists.newArrayList(
            new NeverScheduleCalculator(),
            new CronScheduleCalculator(),
            new FixDelayScheduleCalculator(),
            new FixRateScheduleCalculator()
        );

        for (ScheduleCalculator calculator : calculators) {
            scheduleCalculators.put(calculator.getScheduleType(), calculator);
        }
    }

    /**
     * 根据作业调度类型，创建作业触发时间计算器
     * @param scheduleType 调度方式
     * @return 触发时间计算器
     */
    public static ScheduleCalculator create(ScheduleType scheduleType) {
        return Optional.ofNullable(scheduleCalculators.get(scheduleType))
            .orElseThrow(() -> new IllegalStateException("cannot apply for " + scheduleType));
    }
}
