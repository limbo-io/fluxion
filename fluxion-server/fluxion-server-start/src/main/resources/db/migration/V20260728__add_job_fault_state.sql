ALTER TABLE `fluxion_job`
    ADD COLUMN `dispatch_attempt` int NOT NULL DEFAULT 0 COMMENT 'Worker dispatch attempt',
    ADD COLUMN `lease_owner` varchar(64) DEFAULT NULL COMMENT 'Broker owning the job lease',
    ADD COLUMN `lease_until` datetime(3) DEFAULT NULL COMMENT 'Job lease expiration',
    ADD COLUMN `timeout_at` datetime(3) DEFAULT NULL COMMENT 'Current attempt timeout',
    ADD COLUMN `next_retry_at` datetime(3) DEFAULT NULL COMMENT 'Next retry time';

CREATE INDEX `idx_job_fault_recovery`
    ON `fluxion_job` (`bucket`, `status`, `lease_until`, `next_retry_at`);
