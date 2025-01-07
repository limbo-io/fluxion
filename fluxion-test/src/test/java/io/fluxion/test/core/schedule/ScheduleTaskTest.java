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

package io.fluxion.test.core.schedule;

import com.cronutils.model.CronType;
import io.fluxion.common.utils.time.Formatters;
import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.platform.schedule.Scheduled;
import io.fluxion.platform.schedule.ScheduleOption;
import io.fluxion.platform.schedule.ScheduleType;
import io.fluxion.platform.schedule.calculator.ScheduleCalculator;
import io.fluxion.platform.schedule.calculator.ScheduleCalculatorFactory;
import io.fluxion.platform.schedule.scheduler.ScheduledTaskScheduler;
import io.fluxion.platform.schedule.task.ScheduledTask;
import io.fluxion.platform.schedule.task.ScheduledTaskFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * @author Devil
 */
@Slf4j
class ScheduleTaskTest {

    ScheduledTaskScheduler scheduler = new ScheduledTaskScheduler(100L, TimeUnit.MILLISECONDS);

    @Test
    void testTime() {
        LocalDateTime startAt = LocalDateTime.of(2022, 12, 21, 13, 2, 58, 851);
        Duration delay = Duration.ZERO;
        long startScheduleAt = startAt.toInstant(TimeUtils.zoneOffset()).toEpochMilli();
        log.info("{}", startScheduleAt);
        log.info("{}", startAt.format(Formatters.getFormatter(Formatters.YMD_HMS_SSS)));
        long l = startScheduleAt + delay.toMillis();
        log.info("{}", l);
        log.info("{}", DateFormatUtils.ISO_8601_EXTENDED_DATETIME_FORMAT.format(l));
    }

    @Test
    void testCron() {
        ScheduleOption scheduleOption = new ScheduleOption(ScheduleType.CRON, null, null,
                Duration.ZERO, null,
                "0/1 * * * * ? *", CronType.QUARTZ.name()
        );
        AtomicLong lastTriggerAt = new AtomicLong();
        log.info("times:0 time:{}", System.currentTimeMillis());
        ScheduleCalculator calculator = ScheduleCalculatorFactory.create(ScheduleType.CRON);
        for (int i = 1; i <= 5; i++) {
            Long time = calculator.calculate(new Scheduled() {
                @Override
                public ScheduleOption scheduleOption() {
                    return scheduleOption;
                }

                @Override
                public LocalDateTime lastTriggerAt() {
                    return lastTriggerAt.get() != 0 ? LocalDateTime.ofEpochSecond(lastTriggerAt.get() / 1000, 0, TimeUtils.zoneOffset()) : null;
                }

                @Override
                public LocalDateTime lastFeedbackAt() {
                    return null;
                }
            });
            lastTriggerAt.set(time);
            log.info("times:{} time:{}", i, lastTriggerAt.get());
            log.info("times:{} time format:{}", i, LocalDateTime.ofEpochSecond(lastTriggerAt.get() / 1000, 0, TimeUtils.zoneOffset()).format(Formatters.getFormatter(Formatters.YMD_HMS_SSS)));
        }
    }

    /**
     * 任务间隔 1s 执行 2s
     * 那么执行3 次时间为 2x3+ 1x2 = 8s
     */
    @Test
    void testFixDelayTask() throws InterruptedException {
        log.info("start test {}", TimeUtils.currentLocalDateTime());
        long start = System.currentTimeMillis();
        AtomicLong end = new AtomicLong();
        AtomicInteger times = new AtomicInteger(1);
        scheduler.schedule(ScheduledTaskFactory.fixDelay(
                "123",
                Duration.ofMillis(1000),
                buildConsumer(times, end, 2000)
        ));
        Thread.sleep(10000);
        long cost = end.get() - start;
        log.info("total cost:{}", cost);
        Assertions.assertTrue(cost >= 8000 && cost < 9000);
    }

    /**
     * 任务间隔 1s 执行 2s
     * 那么执行3 次时间为 1 x 2 + 2 = 4s
     */
    @Test
    void testFixRateTask() throws InterruptedException {
        log.info("start test {}", TimeUtils.currentLocalDateTime());
        long start = System.currentTimeMillis();
        AtomicLong end = new AtomicLong();
        AtomicInteger times = new AtomicInteger(1);
        scheduler.schedule(ScheduledTaskFactory.fixRate(
                "123",
                Duration.ofMillis(1000),
                buildConsumer(times, end, 2000)
        ));
        Thread.sleep(5000);
        long cost = end.get() - start;
        log.info("total cost:{}", cost);
        Assertions.assertTrue(cost >= 4000 && cost < 5000);
    }

    @Test
    void testCronTask() throws InterruptedException {
        log.info("start test {}", TimeUtils.currentLocalDateTime());
        long start = System.currentTimeMillis();
        AtomicLong end = new AtomicLong();
        AtomicInteger times = new AtomicInteger(1);
        scheduler.schedule(ScheduledTaskFactory.cron("123",
                "0/1 * * * * ? *", CronType.QUARTZ.name(),
                buildConsumer(times, end, 2000)
        ));
        Thread.sleep(6000); // cron 触发必定从下秒开始
        long cost = end.get() - start;
        log.info("total cost:{} time:{}", cost, DateFormatUtils.format(new Date(), Formatters.YMD_HMS_SSS));
        Assertions.assertTrue(cost >= 5000 && cost < 6000);
    }

    private Consumer<ScheduledTask> buildConsumer(AtomicInteger times, AtomicLong end, long cost) {
        return task -> {
            log.info("execute {} start triggerAt:{} times:{}", task.id(), task.triggerAt(), times.get());
            try {
                if (times.get() > 3) {
                    task.stop();
                    return;
                }
                times.incrementAndGet();
                Thread.sleep(cost);
            } catch (InterruptedException e) {
                log.error("sleep error", e);
            }
            log.info("execute end {}", TimeUtils.currentLocalDateTime());
            end.set(System.currentTimeMillis());
        };
    }

}
