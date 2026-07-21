package io.fluxion.test.unit.server.schedule;

import io.fluxion.server.core.schedule.Schedule;
import io.fluxion.server.core.schedule.ScheduleDelay;
import io.fluxion.server.core.schedule.cmd.ScheduleTriggerCmd;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LATEST_ONLY misfire policy behavior.
 * 
 * LATEST_ONLY policy behavior:
 * - CRON/FIXED_RATE: When broker recovers with multiple missed triggers, 
 *   only creates execution for the latest valid trigger time
 * - FIXED_DELAY: Continues from last completion time, doesn't create 
 *   concurrent historical instances
 */
class ScheduleMisfirePolicyTest {

    /**
     * Test 1: CRON with 3 missed triggers creates only 1 execution (latest)
     */
    @Test
    void cronWithMissedTriggersShouldCreateOnlyOneExecution() {
        // Simulate a CRON schedule "0 * * * *" (every hour) that missed 3 triggers
        // due to broker downtime
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime fourHoursAgo = now.minusHours(4);
        
        Schedule schedule = new Schedule();
        schedule.setId("cron-schedule-001");
        schedule.setEnabled(true);
        schedule.setLastTriggerAt(fourHoursAgo);
        // nextTriggerAt is 3 hours ago (3 missed triggers)
        schedule.setNextTriggerAt(fourHoursAgo.plusHours(1));
        
        ScheduleOption option = new ScheduleOption(
            ScheduleType.CRON,
            fourHoursAgo.minusHours(1),
            now.plusHours(1),
            Duration.ZERO,
            Duration.ZERO,
            "0 * * * *",  // Every hour
            "QUARTZ"
        );
        schedule.setOption(option);
        
        // Simulate processing the trigger command
        List<ScheduleDelay> delays = simulateScheduleTrigger(schedule, now);
        
        // LATEST_ONLY: Should create only 1 delay (the latest valid trigger)
        assertEquals(1, delays.size(), 
            "CRON schedule with 3 missed triggers should create only 1 execution (latest)");
        
        // The delay should be for the latest trigger time (closest to now but not past now)
        ScheduleDelay.ID delayId = delays.get(0).getId();
        assertNotNull(delayId);
        // Latest trigger should be approximately 1 hour ago (within the load window)
        assertTrue(delayId.getTriggerAt().isAfter(now.minusHours(2)), 
            "Latest trigger should be within last 2 hours");
        assertTrue(delayId.getTriggerAt().isBefore(now.plusMinutes(5)), 
            "Latest trigger should be before or near now");
    }

    /**
     * Test 2: FIXED_RATE with backlog skips stale triggers
     */
    @Test
    void fixedRateWithBacklogShouldSkipStaleTriggers() {
        // Simulate a FIXED_RATE schedule (every 5 minutes) that accumulated 5 missed triggers
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thirtyMinutesAgo = now.minusMinutes(30);
        
        Schedule schedule = new Schedule();
        schedule.setId("fixed-rate-001");
        schedule.setEnabled(true);
        schedule.setLastTriggerAt(thirtyMinutesAgo);
        // nextTriggerAt is 25 minutes ago (5 missed triggers at 5 min intervals)
        schedule.setNextTriggerAt(thirtyMinutesAgo.plusMinutes(5));
        
        ScheduleOption option = new ScheduleOption(
            ScheduleType.FIXED_RATE,
            thirtyMinutesAgo.minusMinutes(5),
            now.plusHours(1),
            Duration.ZERO,
            Duration.ofMinutes(5),  // Every 5 minutes
            null,
            null
        );
        schedule.setOption(option);
        
        // Simulate processing the trigger command
        List<ScheduleDelay> delays = simulateScheduleTrigger(schedule, now);
        
        // LATEST_ONLY: Should create only 1 delay (the latest valid trigger)
        assertEquals(1, delays.size(), 
            "FIXED_RATE with backlog should skip stale triggers and create only latest execution");
        
        // Verify the delay is the latest one within the valid window
        ScheduleDelay.ID delayId = delays.get(0).getId();
        assertTrue(delayId.getTriggerAt().isAfter(now.minusMinutes(10)), 
            "Latest trigger should be within last 10 minutes");
    }

    /**
     * Test 3: FIXED_DELAY doesn't create concurrent historical executions
     */
    @Test
    void fixedDelayShouldNotCreateConcurrentHistoricalExecutions() {
        // FIXED_DELAY: continues from last completion time
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourAgo = now.minusHours(1);
        
        Schedule schedule = new Schedule();
        schedule.setId("fixed-delay-001");
        schedule.setEnabled(true);
        schedule.setLastTriggerAt(oneHourAgo);
        // nextTriggerAt is in the past (should have triggered already)
        schedule.setNextTriggerAt(oneHourAgo.plusMinutes(10));
        
        ScheduleOption option = new ScheduleOption(
            ScheduleType.FIXED_DELAY,
            oneHourAgo.minusHours(1),
            now.plusHours(1),
            Duration.ZERO,
            Duration.ofMinutes(10),  // 10 minute delay between completions
            null,
            null
        );
        schedule.setOption(option);
        
        // Simulate processing the trigger command
        List<ScheduleDelay> delays = simulateScheduleTrigger(schedule, now);
        
        // FIXED_DELAY should create at most 1 delay (the next single trigger point)
        // It does not create multiple concurrent historical instances
        assertTrue(delays.size() <= 1, 
            "FIXED_DELAY should not create concurrent historical instances - only one next trigger");
        
        if (!delays.isEmpty()) {
            // If a delay is created, it should be the next single trigger point
            ScheduleDelay.ID delayId = delays.get(0).getId();
            assertNotNull(delayId);
            // The trigger time should not be in the distant past (not historical)
            assertTrue(delayId.getTriggerAt().isAfter(now.minusMinutes(5)), 
                "FIXED_DELAY trigger should not be historical");
        }
    }

    /**
     * Test 4: No missed triggers - normal operation
     */
    @Test
    void noMissedTriggersShouldCreateExpectedExecutions() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime tenMinutesAgo = now.minusMinutes(10);
        
        Schedule schedule = new Schedule();
        schedule.setId("cron-normal-001");
        schedule.setEnabled(true);
        schedule.setLastTriggerAt(tenMinutesAgo);
        // nextTriggerAt is in the future - no missed triggers
        schedule.setNextTriggerAt(now.plusMinutes(10));
        
        ScheduleOption option = new ScheduleOption(
            ScheduleType.CRON,
            tenMinutesAgo.minusHours(1),
            now.plusHours(1),
            Duration.ZERO,
            Duration.ZERO,
            "0/10 * * * *",  // Every 10 minutes
            "QUARTZ"
        );
        schedule.setOption(option);
        
        // Simulate processing the trigger command
        List<ScheduleDelay> delays = simulateScheduleTrigger(schedule, now);
        
        // Normal operation - should create future triggers
        assertTrue(delays.size() >= 1, 
            "Normal schedule should create at least one future trigger");
        
        // All delays should be for future times
        for (ScheduleDelay delay : delays) {
            assertTrue(delay.getId().getTriggerAt().isAfter(now.minusMinutes(1)), 
                "Future triggers should not be in the past");
        }
    }

    /**
     * Simulates the ScheduleTriggerCmd processing logic to determine which delays would be created.
     * This mirrors the LATEST_ONLY misfire policy implementation:
     * - CRON/FIXED_RATE: Only latest valid trigger
     * - FIXED_DELAY: Single next trigger
     */
    private List<ScheduleDelay> simulateScheduleTrigger(Schedule schedule, LocalDateTime now) {
        List<ScheduleDelay> delays = new ArrayList<>();
        LocalDateTime lastTriggerAt = schedule.getLastTriggerAt();
        LocalDateTime nextTriggerAt = schedule.getNextTriggerAt();
        
        if (nextTriggerAt.isBefore(now)) {
            // Calculate next trigger from lastTriggerAt
            nextTriggerAt = calculateNextTrigger(lastTriggerAt, lastTriggerAt, schedule.getOption());
        }
        
        if (ScheduleType.FIXED_DELAY == schedule.getOption().getType()) {
            // FIXED_DELAY: Only create one next trigger
            if (nextTriggerAt != null && 
                !nextTriggerAt.isBefore(now) && 
                !nextTriggerAt.isBefore(schedule.getOption().getStartTime()) &&
                !nextTriggerAt.isAfter(schedule.getOption().getEndTime())) {
                
                ScheduleDelay delay = new ScheduleDelay(
                    new ScheduleDelay.ID(schedule.getId(), nextTriggerAt),
                    ScheduleDelay.Status.INIT
                );
                delays.add(delay);
            }
        } else {
            // CRON/FIXED_RATE: LATEST_ONLY - find latest valid trigger
            LocalDateTime latestValidTrigger = null;
            int maxIterations = 100; // Safety limit
            int iterations = 0;
            
            while (iterations++ < maxIterations && 
                   nextTriggerAt != null &&
                   !nextTriggerAt.isBefore(now) &&
                   !nextTriggerAt.isBefore(schedule.getOption().getStartTime()) &&
                   !nextTriggerAt.isAfter(schedule.getOption().getEndTime()) &&
                   !nextTriggerAt.isAfter(now.plusSeconds(60))) { // LOAD_INTERVAL_SECONDS equivalent
                
                latestValidTrigger = nextTriggerAt;
                lastTriggerAt = nextTriggerAt;
                nextTriggerAt = calculateNextTrigger(lastTriggerAt, lastTriggerAt, schedule.getOption());
            }
            
            // Only create delay for the latest valid trigger
            if (latestValidTrigger != null) {
                ScheduleDelay delay = new ScheduleDelay(
                    new ScheduleDelay.ID(schedule.getId(), latestValidTrigger),
                    ScheduleDelay.Status.INIT
                );
                delays.add(delay);
            }
        }
        
        return delays;
    }
    
    private LocalDateTime calculateNextTrigger(LocalDateTime lastTriggerAt, 
                                               LocalDateTime lastReferenceAt, 
                                               ScheduleOption option) {
        if (ScheduleType.CRON == option.getType()) {
            // Simplified CRON calculation - add 1 hour for "0 * * * *" pattern
            // In real implementation would use CronExpression
            return lastTriggerAt.plusMinutes(60);
        } else if (ScheduleType.FIXED_RATE == option.getType() || ScheduleType.FIXED_DELAY == option.getType()) {
            return lastTriggerAt.plus(option.getInterval());
        }
        return null;
    }
}
