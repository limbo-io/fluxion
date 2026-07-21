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

-- Add lease fields for broker claim mechanism to prevent concurrent scheduling
ALTER TABLE `fluxion_schedule_delay`
    ADD COLUMN `lease_owner` varchar(64) DEFAULT NULL COMMENT 'Broker ID that owns the lease',
    ADD COLUMN `lease_until` datetime(3) DEFAULT NULL COMMENT 'Lease expiration time',
    ADD COLUMN `attempt` int unsigned NOT NULL DEFAULT 0 COMMENT 'Number of claim attempts';

-- Index for efficient lease queries: finding delays to claim and renew
CREATE INDEX `idx_delay_lease` ON `fluxion_schedule_delay` (`bucket`, `status`, `trigger_at`, `lease_until`);
