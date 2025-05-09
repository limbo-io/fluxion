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

package io.fluxion.server.infrastructure.schedule.calculator;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import io.fluxion.server.infrastructure.schedule.Calculable;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * CRON调度时间计算器
 *
 * @author Brozen
 * @since 2021-05-21
 */
@Slf4j
public class CronScheduleCalculator implements ScheduleCalculator {

    public static Cron getCron(String cron, String cronType) {
        // 校验CRON表达式
        CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.valueOf(cronType));
        CronParser parser = new CronParser(cronDefinition);
        return parser.parse(cron);
    }

    /**
     * 通过此策略计算下一次触发调度的时间戳。如果不应该被触发，返回0或负数。
     *
     * @param calculable 待调度对象
     * @return 下次触发调度的时间戳，当返回非正数时，表示作业不会有触发时间。
     */
    @Override
    public LocalDateTime calculate(Calculable calculable) {
        ScheduleOption scheduleOption = calculable.scheduleOption();
        // 计算下一次调度
        String cron = scheduleOption.getCron();
        String cronType = scheduleOption.getCronType();
        try {
            ExecutionTime executionTime = ExecutionTime.forCron(getCron(cron, cronType));

            // 解析下次触发时间
            Optional<ZonedDateTime> nextSchedule = executionTime.nextExecution(
                calculable.lastTriggerAt() == null ? ZonedDateTime.now()
                    : calculable.lastTriggerAt().atZone(ZoneId.systemDefault())
            );
            if (!nextSchedule.isPresent()) {
                log.error("cron expression {} {} next schedule is null", cron, cronType);
                return null;
            }
            return nextSchedule.get().toLocalDateTime();
        } catch (Exception e) {
            log.error("parse cron expression {} {} failed!", cron, cronType, e);
            return null;
        }
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.CRON;
    }
}
