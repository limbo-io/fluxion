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

import java.time.Instant;
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
     * <p>
     * CRON 是日历语义：以 schedule 配置的 IANA timeZone 解释（Cron/回拨/夏令时前跳按该时区规则），
     * 计算结果返回绝对时刻（见 CONTEXT.md「统一时间模型」），不依赖任何节点默认时区。
     *
     * @param calculable 待调度对象
     * @return 下次触发调度的绝对时刻，null 表示无下次触发。
     */
    @Override
    public Instant calculate(Calculable calculable) {
        ScheduleOption scheduleOption = calculable.scheduleOption();
        // 计算下一次调度
        String cron = scheduleOption.getCron();
        String cronType = scheduleOption.getCronType();
        ZoneId tz = scheduleOption.getTimeZone();
        try {
            ExecutionTime executionTime = ExecutionTime.forCron(getCron(cron, cronType));

            // 解析下次触发时间：上次触发时刻按配置时区展开为日历时间参与 CRON 匹配
            ZonedDateTime lastTrigger = calculable.lastTriggerAt() == null
                ? ZonedDateTime.now(tz)
                : calculable.lastTriggerAt().atZone(tz);
            Optional<ZonedDateTime> nextSchedule = executionTime.nextExecution(lastTrigger);
            if (!nextSchedule.isPresent()) {
                log.error("cron expression {} {} next schedule is null", cron, cronType);
                return null;
            }
            return nextSchedule.get().toInstant();
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