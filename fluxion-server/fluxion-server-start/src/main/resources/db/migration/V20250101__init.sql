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
    `type`       varchar(255)    NOT NULL DEFAULT '',
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
    `ref_id`      varchar(255)    NOT NULL DEFAULT '',
    `ref_type`    tinyint         NOT NULL DEFAULT 0,
    `version`     varchar(255)    NOT NULL DEFAULT '',
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
    `ref_id`     varchar(255)    NOT NULL DEFAULT '',
    `ref_type`   tinyint         NOT NULL DEFAULT 0,
    `tag_name`   varchar(255)    NOT NULL DEFAULT '',
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
    `name`       varchar(255)    NOT NULL DEFAULT '',
    `owner`      varchar(255)    NOT NULL DEFAULT '',
    `expire_at`  datetime(6)     NOT NULL,
    `is_deleted` bit(1)          NOT NULL DEFAULT 0,
    `created_at` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_lock` (`name`)
);

CREATE TABLE `fluxion_flow`
(
    `id`            bigint unsigned NOT NULL AUTO_INCREMENT,
    `flow_id`       varchar(255)    NOT NULL DEFAULT '',
    `name`          varchar(255)    NOT NULL DEFAULT '',
    `description`   varchar(255)    NOT NULL DEFAULT '',
    `run_version`   varchar(255)    NOT NULL DEFAULT '',
    `draft_version` varchar(255)    NOT NULL DEFAULT '',
    `is_deleted`    bit(1)          NOT NULL DEFAULT 0,
    `created_at`    datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_flow` (`flow_id`)
);

CREATE TABLE `fluxion_trigger`
(
    `id`             bigint unsigned NOT NULL AUTO_INCREMENT,
    `trigger_id`     varchar(255)    NOT NULL DEFAULT '',
    `name`           varchar(255)    NOT NULL DEFAULT '',
    `description`    varchar(255)    NOT NULL DEFAULT '',
    `type`           varchar(255)    NOT NULL DEFAULT '',
    `execute_config` MEDIUMTEXT,
    `trigger_config` MEDIUMTEXT,
    `is_enabled`     bit(1)          NOT NULL DEFAULT 0,
    `is_deleted`     bit(1)          NOT NULL DEFAULT 0,
    `created_at`     datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_trigger` (`trigger_id`)
);

CREATE TABLE `fluxion_broker`
(
    `id`             bigint unsigned NOT NULL AUTO_INCREMENT,
    `host`           varchar(255)    NOT NULL DEFAULT '',
    `port`           int                      DEFAULT 0,
    `protocol`       varchar(255)    NOT NULL DEFAULT '',
    `load`           int                      DEFAULT 0,
    `last_heartbeat` datetime(6)              DEFAULT NULL,
    `is_deleted`     bit(1)          NOT NULL DEFAULT 0,
    `created_at`     datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_broker` (`host`, `port`),
    KEY `idx_last_heartbeat` (`last_heartbeat`)
);

CREATE TABLE `fluxion_app`
(
    `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
    `app_id`     varchar(255)    NOT NULL DEFAULT '',
    `app_name`   varchar(255)    NOT NULL DEFAULT '',
    `broker_id`  varchar(255)    NOT NULL DEFAULT '',
    `is_deleted` bit(1)          NOT NULL DEFAULT 0,
    `created_at` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_app` (`app_id`),
    KEY `idx_broker` (`broker_id`)
);

CREATE TABLE `fluxion_execution`
(
    `id`           bigint unsigned NOT NULL AUTO_INCREMENT,
    `execution_id` varchar(255)    NOT NULL DEFAULT '',
    `trigger_id`   varchar(255)    NOT NULL DEFAULT '',
    `trigger_type` varchar(255)    NOT NULL DEFAULT '',
    `ref_id`       varchar(255)    NOT NULL DEFAULT '',
    `ref_type`     varchar(255)    NOT NULL DEFAULT '',
    `ref_version`  varchar(255)    NOT NULL DEFAULT '',
    `status`       varchar(255)    NOT NULL DEFAULT '',
    `trigger_at`   datetime(6)              DEFAULT NULL,
    `start_at`     datetime(6)              DEFAULT NULL,
    `end_at`       datetime(6)              DEFAULT NULL,
    `is_deleted`   bit(1)          NOT NULL DEFAULT 0,
    `created_at`   datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_execution` (`execution_id`)
);

CREATE TABLE `fluxion_schedule_delay`
(
    `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
    `schedule_id` varchar(255)    NOT NULL DEFAULT '',
    `trigger_at`  datetime(6)     NOT NULL,
    `broker_id`   varchar(255)    NOT NULL DEFAULT '',
    `status`      varchar(255)    NOT NULL DEFAULT '',
    `is_deleted`  bit(1)          NOT NULL DEFAULT 0,
    `created_at`  datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_schedule_delay` (`schedule_id`, `trigger_at`),
    KEY `idx_broker_status` (`broker_id`, `status`)
);

CREATE TABLE `fluxion_schedule`
(
    `id`                 bigint unsigned NOT NULL AUTO_INCREMENT,
    `schedule_id`        varchar(255)    NOT NULL DEFAULT '',
    `broker_id`          varchar(255)    NOT NULL DEFAULT '',
    `schedule_type`      varchar(255)    NOT NULL DEFAULT '',
    `start_time`         datetime(6)              DEFAULT NULL,
    `end_time`           datetime(6)              DEFAULT NULL,
    `schedule_delay`     bigint                   DEFAULT 0,
    `schedule_interval`  bigint                   DEFAULT NULL,
    `schedule_cron`      varchar(255)    NOT NULL DEFAULT '',
    `schedule_cron_type` varchar(255)    NOT NULL DEFAULT '',
    `last_trigger_at`    datetime(6)              DEFAULT NULL,
    `last_feedback_at`   datetime(6)              DEFAULT NULL,
    `next_trigger_at`    datetime(6)              DEFAULT NULL,
    `is_enabled`         bit(1)          NOT NULL DEFAULT 0,
    `is_deleted`         bit(1)          NOT NULL DEFAULT 0,
    `created_at`         datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`         datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_schedule` (`schedule_id`),
    KEY `idx_broker_next_trigger_start_end` (`broker_id`, `next_trigger_at`, `start_time`, `end_time`)
);

