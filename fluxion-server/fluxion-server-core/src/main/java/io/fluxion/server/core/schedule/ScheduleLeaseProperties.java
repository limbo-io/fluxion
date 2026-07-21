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

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Configuration properties for schedule delay lease management.
 *
 * @author Devil
 */
@Slf4j
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "fluxion.schedule.lease")
public class ScheduleLeaseProperties {

    /**
     * Lease duration in seconds. Leases expire after this duration if not renewed.
     * Default: 15 seconds
     */
    private int duration = 15;

    /**
     * Interval between lease renewals in seconds.
     * Must be less than lease duration to ensure leases don't expire between renewals.
     * Default: 10 seconds
     */
    private int renewInterval = 10;

    /**
     * Interval between scanning for expired leases to reclaim in seconds.
     * Default: 5 seconds
     */
    private int reclaimInterval = 5;

    @PostConstruct
    public void validate() {
        if (renewInterval >= duration) {
            throw new IllegalStateException(
                "Invalid schedule lease configuration: renewInterval (" + renewInterval +
                ") must be less than duration (" + duration + ") to prevent lease expiration"
            );
        }
        log.info("ScheduleLeaseProperties loaded: duration={}s, renewInterval={}s, reclaimInterval={}s",
            duration, renewInterval, reclaimInterval);
    }
}
