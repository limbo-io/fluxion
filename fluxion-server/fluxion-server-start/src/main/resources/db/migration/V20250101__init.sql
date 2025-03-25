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

CREATE TABLE `fluxion_version`
(
    `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
    `ref_id`      varchar(64)     NOT NULL,
    `ref_type`    varchar(64)     NOT NULL,
    `version`     varchar(64)     NOT NULL,
    `description` varchar(255)    NOT NULL DEFAULT '',
    `config`      MEDIUMTEXT,
    `is_deleted`  bit(1)          NOT NULL DEFAULT 0,
    `created_at`  datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_version` (`ref_id`, `ref_type`, `version`)
);

CREATE TABLE `fluxion_tag`
(
    `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
    `ref_id`     varchar(64)     NOT NULL,
    `ref_type`   varchar(64)     NOT NULL,
    `tag_name`   varchar(128)    NOT NULL,
    `tag_value`  varchar(255)    NOT NULL DEFAULT '',
    `is_deleted` bit(1)          NOT NULL DEFAULT 0,
    `created_at` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tag` (`ref_id`, `ref_type`, `tag_name`, `tag_value`)
);

CREATE TABLE `fluxion_lock`
(
    `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
    `name`       varchar(255)    NOT NULL,
    `owner`      varchar(255)    NOT NULL,
    `expire_at`  datetime(6)     NOT NULL,
    `is_deleted` bit(1)          NOT NULL DEFAULT 0,
    `created_at` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_lock` (`name`)
);

CREATE TABLE `fluxion_broker`
(
    `id`             bigint unsigned NOT NULL AUTO_INCREMENT,
    `host`           varchar(255)    NOT NULL,
    `port`           int                      DEFAULT 0,
    `protocol`       varchar(64)     NOT NULL,
    `broker_load`    int                      DEFAULT 0,
    `last_heartbeat` datetime(6)              DEFAULT NULL,
    `is_deleted`     bit(1)          NOT NULL DEFAULT 0,
    `created_at`     datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_broker` (`host`, `port`),
    KEY `idx_last_heartbeat` (`last_heartbeat`)
);

CREATE TABLE `fluxion_bucket`
(
    `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
    `bucket`     int unsigned    NOT NULL,
    `broker_id`  varchar(64)     NOT NULL,
    `is_deleted` bit(1)          NOT NULL DEFAULT 0,
    `created_at` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_bucket` (`bucket`),
    KEY `idx_broker` (`broker_id`)
);

CREATE TABLE `fluxion_app`
(
    `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
    `app_id`     varchar(64)     NOT NULL,
    `app_name`   varchar(255)    NOT NULL,
    `is_deleted` bit(1)          NOT NULL DEFAULT 0,
    `created_at` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_app` (`app_id`)
);

CREATE TABLE `fluxion_flow`
(
    `id`              bigint unsigned NOT NULL AUTO_INCREMENT,
    `flow_id`         varchar(64)     NOT NULL,
    `name`            varchar(255)    NOT NULL,
    `description`     varchar(255)    NOT NULL DEFAULT '',
    `publish_version` varchar(64)     NOT NULL DEFAULT '',
    `draft_version`   varchar(64)     NOT NULL DEFAULT '',
    `is_deleted`      bit(1)          NOT NULL DEFAULT 0,
    `created_at`      datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_flow` (`flow_id`)
);

CREATE TABLE `fluxion_trigger`
(
    `id`              bigint unsigned NOT NULL AUTO_INCREMENT,
    `trigger_id`      varchar(64)     NOT NULL,
    `name`            varchar(255)    NOT NULL,
    `description`     varchar(255)    NOT NULL DEFAULT '',
    `publish_version` varchar(64)     NOT NULL DEFAULT '',
    `draft_version`   varchar(64)     NOT NULL DEFAULT '',
    `is_enabled`      bit(1)          NOT NULL DEFAULT 0,
    `is_deleted`      bit(1)          NOT NULL DEFAULT 0,
    `created_at`      datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_trigger` (`trigger_id`)
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
    `trigger_at`         datetime(6)              DEFAULT NULL,
    `start_at`           datetime(6)              DEFAULT NULL,
    `end_at`             datetime(6)              DEFAULT NULL,
    `is_deleted`         bit(1)          NOT NULL DEFAULT 0,
    `created_at`         datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`         datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_execution` (`execution_id`),
    UNIQUE KEY `uk_executable_trigger` (`executable_id`, `trigger_at`, `executable_type`)
);

CREATE TABLE `fluxion_schedule`
(
    `id`                 bigint unsigned NOT NULL AUTO_INCREMENT,
    `schedule_id`        varchar(64)     NOT NULL,
    `bucket`             int unsigned    NOT NULL,
    `schedule_type`      varchar(64)     NOT NULL,
    `start_time`         datetime(6)              DEFAULT NULL,
    `end_time`           datetime(6)              DEFAULT NULL,
    `schedule_delay`     bigint                   DEFAULT 0,
    `schedule_interval`  bigint                   DEFAULT NULL,
    `schedule_cron`      varchar(128)    NOT NULL DEFAULT '',
    `schedule_cron_type` varchar(32)     NOT NULL DEFAULT '',
    `last_trigger_at`    datetime(6)              DEFAULT NULL,
    `last_feedback_at`   datetime(6)              DEFAULT NULL,
    `next_trigger_at`    datetime(6)              DEFAULT NULL,
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
    `trigger_at`  datetime(6)     NOT NULL,
    `bucket`      int unsigned    NOT NULL,
    `status`      varchar(32)     NOT NULL,
    `is_deleted`  bit(1)          NOT NULL DEFAULT 0,
    `created_at`  datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_schedule_delay` (`schedule_id`, `trigger_at`),
    KEY `idx_trigger_bucket_status` (`trigger_at`, `bucket`, `status`)
);

CREATE TABLE `fluxion_task`
(
    `id`             bigint unsigned NOT NULL AUTO_INCREMENT,
    `task_id`        varchar(64)     NOT NULL,
    `execution_id`   varchar(64)     NOT NULL,
    `task_type`      varchar(32)     NOT NULL,
    `ref_id`         varchar(64)     NOT NULL,
    `status`         varchar(32)     NOT NULL,
    `trigger_at`     datetime(6)     NOT NULL,
    `start_at`       datetime(6)              DEFAULT NULL,
    `end_at`         datetime(6)              DEFAULT NULL,
    `worker_address` varchar(64)     NOT NULL DEFAULT '',
    `last_report_at` datetime(6)              DEFAULT NULL,
    `retry_times`    int unsigned    NOT NULL,
    `is_deleted`     bit(1)          NOT NULL DEFAULT 0,
    `created_at`     datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task` (`task_id`),
    KEY `idx_execution_ref_type` (`execution_id`, `ref_id`, `task_type`)
);

CREATE TABLE `fluxion_worker`
(
    `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
    `worker_id`  varchar(64)     NOT NULL,
    `app_id`     varchar(64)     NOT NULL,
    `host`       varchar(255)    NOT NULL,
    `port`       int                      DEFAULT 0,
    `protocol`   varchar(64)     NOT NULL,
    `status`     varchar(32)     NOT NULL,
    `is_enabled` bit(1)          NOT NULL DEFAULT 0,
    `is_deleted` bit(1)          NOT NULL DEFAULT 0,
    `created_at` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_worker` (`worker_id`),
    KEY `idx_app` (`app_id`)
);

CREATE TABLE `fluxion_worker_executor`
(
    `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
    `worker_id`  varchar(64)     NOT NULL,
    `name`       varchar(255)    NOT NULL,
    `is_deleted` bit(1)          NOT NULL DEFAULT 0,
    `created_at` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_worker_executor` (`worker_id`, `name`)
);

CREATE TABLE `fluxion_worker_metric`
(
    `id`                  bigint unsigned NOT NULL AUTO_INCREMENT,
    `worker_id`           varchar(64)     NOT NULL,
    `cpu_processors`      int             NOT NULL,
    `cpu_load`            float           NOT NULL,
    `free_memory`         bigint          NOT NULL,
    `available_queue_num` int             NOT NULL,
    `last_heartbeat_at`   datetime(6)     NOT NULL,
    `is_deleted`          bit(1)          NOT NULL DEFAULT 0,
    `created_at`          datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_worker_metric` (`worker_id`)
);

