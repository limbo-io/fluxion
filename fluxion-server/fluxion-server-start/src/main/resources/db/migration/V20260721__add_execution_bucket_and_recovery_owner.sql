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

-- Add bucket column for execution filtering by broker assignment
-- Bucket is computed as: Math.abs(resourceId.hashCode()) % 64 + 1
ALTER TABLE `fluxion_execution`
    ADD COLUMN `bucket` int DEFAULT NULL COMMENT 'Bucket number (1-64) for broker assignment';

-- Add recovery_owner column to track broker claiming for recovery (separate from workerId)
ALTER TABLE `fluxion_execution`
    ADD COLUMN `recovery_owner` varchar(64) DEFAULT NULL COMMENT 'Broker ID that claimed this execution for recovery';

-- Index for efficient bucket-based recovery queries
CREATE INDEX `idx_execution_bucket` ON `fluxion_execution` (`bucket`, `status`);

-- Index for recovery owner queries
CREATE INDEX `idx_execution_recovery_owner` ON `fluxion_execution` (`recovery_owner`);

-- Backfill bucket values for existing executions where possible
-- Note: This is a best-effort migration; executions will have bucket = NULL
-- and will be recovered through the fallback path (all unbucketed executions)
-- until they get a bucket assigned on next update
UPDATE `fluxion_execution` 
SET `bucket` = (ABS(CRC32(`execution_id`)) % 64 + 1)
WHERE `bucket` IS NULL 
AND `status` IN ('running', 'restarted');
