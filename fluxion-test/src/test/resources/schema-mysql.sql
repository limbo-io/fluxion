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

-- Testcontainers initialization script for MySQL integration tests
-- Combines all migrations into a single schema for fast test setup

CREATE TABLE `fluxion_id`
(
    `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
    `type`       varchar(64)     NOT NULL,
    `current_id` bigint unsigned NOT NULL DEFAULT 0,
    `step`       int             NOT NULL DEFAULT 0,
    `is_deleted` bit(1)          NOT NULL DEFAULT 0,
    `created_at` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_id` (`type`)
);

CREATE TABLE `fluxion_lock`
(
    `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
    `name`       varchar(255)    NOT NULL,
    `owner`      varchar(255)    NOT NULL,
    `expire_at`  datetime(3)     NOT NULL,
    `is_deleted` bit(1)          NOT NULL DEFAULT 0,
    `created_at` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_lock` (`name`)
);

CREATE TABLE `fluxion_broker`
(
    `id`                bigint unsigned NOT NULL AUTO_INCREMENT,
    `host`              varchar(255)    NOT NULL,
    `port`              int                      DEFAULT 0,
    `protocol`          varchar(64)     NOT NULL,
    `broker_load`       int                      DEFAULT 0,
    `last_heartbeat_at` datetime(3)              DEFAULT NULL,
    `is_deleted`        bit(1)          NOT NULL DEFAULT 0,
    `created_at`        datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_broker` (`host`, `port`),
    KEY `idx_broker_last_heartbeat` (`last_heartbeat_at`)
);

CREATE TABLE `fluxion_execution`
(
    `id`                 bigint unsigned NOT NULL AUTO_INCREMENT,
    `execution_id`       varchar(64)     NOT NULL,
    `trigger_id`         varchar(64)     NOT NULL,
    `trigger_type`       varchar(64)     NOT NULL,
    `executable_id`      varchar(64)     NOT NULL,
    `executable_type`    varchar(64)     NOT NULL,
    `executable_version` varchar(64)     NOT NULL,
    `status`             varchar(32)     NOT NULL,
    `trigger_at`         datetime(3)              DEFAULT NULL,
    `start_at`           datetime(3)              DEFAULT NULL,
    `end_at`             datetime(3)              DEFAULT NULL,
    `worker_id`          varchar(64)              DEFAULT NULL COMMENT 'Worker ID executing this execution',
    `dispatch_attempt`   int NOT NULL DEFAULT 0 COMMENT 'Number of dispatch attempts',
    `state_updated_at`   datetime(3)              DEFAULT NULL COMMENT 'Last state update timestamp',
    `lease_owner`        varchar(64)              DEFAULT NULL COMMENT 'Broker ID that owns the lease',
    `lease_until`        datetime(3)              DEFAULT NULL COMMENT 'Lease expiration time',
    `bucket`             int                      DEFAULT NULL COMMENT 'Bucket number (1-64) for broker assignment',
    `recovery_owner`     varchar(64)              DEFAULT NULL COMMENT 'Broker ID that claimed this execution for recovery',
    `is_deleted`         bit(1)          NOT NULL DEFAULT 0,
    `created_at`         datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`         datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_execution` (`execution_id`),
    KEY `idx_execution_lease` (`status`, `lease_owner`, `lease_until`),
    KEY `idx_execution_bucket` (`bucket`, `status`)
);

CREATE TABLE `fluxion_schedule`
(
    `id`                 bigint unsigned NOT NULL AUTO_INCREMENT,
    `schedule_id`        varchar(64)     NOT NULL,
    `bucket`             int unsigned    NOT NULL,
    `schedule_type`      varchar(64)     NOT NULL,
    `start_time`         datetime(3)              DEFAULT NULL,
    `end_time`           datetime(3)              DEFAULT NULL,
    `schedule_delay`     bigint                   DEFAULT 0,
    `schedule_interval`  bigint                   DEFAULT NULL,
    `schedule_cron`      varchar(128)    NOT NULL DEFAULT '',
    `schedule_cron_type` varchar(32)     NOT NULL DEFAULT '',
    `last_trigger_at`    datetime(3)              DEFAULT NULL,
    `last_feedback_at`   datetime(3)              DEFAULT NULL,
    `next_trigger_at`    datetime(3)              DEFAULT NULL,
    `is_enabled`         bit(1)          NOT NULL DEFAULT 0,
    `is_deleted`         bit(1)          NOT NULL DEFAULT 0,
    `created_at`         datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`         datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_schedule` (`schedule_id`),
    KEY `idx_next_trigger_start_end_bucket` (`next_trigger_at`, `start_time`, `end_time`, `bucket`)
);

CREATE TABLE `fluxion_schedule_delay`
(
    `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
    `schedule_id` varchar(64)     NOT NULL,
    `trigger_at`  datetime(3)     NOT NULL,
    `delay_id`    varchar(128)    NOT NULL,
    `bucket`      int unsigned    NOT NULL,
    `status`      varchar(32)     NOT NULL,
    `lease_owner` varchar(64)              DEFAULT NULL COMMENT 'Broker ID that owns the lease',
    `lease_until` datetime(3)              DEFAULT NULL COMMENT 'Lease expiration time',
    `attempt`     int unsigned    NOT NULL DEFAULT 0 COMMENT 'Number of claim attempts',
    `is_deleted`  bit(1)          NOT NULL DEFAULT 0,
    `created_at`  datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_schedule_delay` (`schedule_id`, `trigger_at`),
    KEY `idx_delay_lease` ON `fluxion_schedule_delay` (`bucket`, `status`, `trigger_at`, `lease_until`)
);

CREATE TABLE `fluxion_job`
(
    `id`             bigint unsigned NOT NULL AUTO_INCREMENT,
    `job_id`         varchar(64)     NOT NULL,
    `execution_id`   varchar(64)     NOT NULL,
    `bucket`         int unsigned    NOT NULL,
    `job_type`       varchar(32)     NOT NULL,
    `ref_id`         varchar(64)     NOT NULL,
    `status`         varchar(32)     NOT NULL,
    `trigger_at`     datetime(3)     NOT NULL,
    `start_at`       datetime(3)              DEFAULT NULL,
    `end_at`         datetime(3)              DEFAULT NULL,
    `worker_address` varchar(64)     NOT NULL DEFAULT '',
    `last_report_at` datetime(3)              DEFAULT NULL,
    `retry_times`    int unsigned    NOT NULL,
    `result`         TEXT,
    `error_msg`      TEXT,
    `monitor`        varchar(255)    NOT NULL DEFAULT '',
    `is_deleted`     bit(1)          NOT NULL DEFAULT 0,
    `created_at`     datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_job` (`job_id`),
    UNIQUE KEY `uk_job_execution` (`execution_id`, `ref_id`),
    KEY `idx_job_status_trigger` (`job_id`, `bucket`, `trigger_at`, `status`),
    KEY `idx_job_status_report` (`job_id`, `bucket`, `last_report_at`, `status`)
);
