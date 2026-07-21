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

-- Add lease and dispatch tracking fields for execution fault tolerance
ALTER TABLE `fluxion_execution`
    ADD COLUMN `worker_id` varchar(64) DEFAULT NULL COMMENT 'Worker ID executing this execution',
    ADD COLUMN `dispatch_attempt` int NOT NULL DEFAULT 0 COMMENT 'Number of dispatch attempts',
    ADD COLUMN `state_updated_at` datetime(3) DEFAULT NULL COMMENT 'Last state update timestamp',
    ADD COLUMN `lease_owner` varchar(64) DEFAULT NULL COMMENT 'Broker ID that owns the lease',
    ADD COLUMN `lease_until` datetime(3) DEFAULT NULL COMMENT 'Lease expiration time';

-- Index for efficient recovery queries: finding active executions by lease
CREATE INDEX `idx_execution_lease` ON `fluxion_execution` (`status`, `lease_owner`, `lease_until`);
