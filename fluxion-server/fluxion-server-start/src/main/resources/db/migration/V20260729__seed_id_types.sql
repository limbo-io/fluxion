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

-- 固定 ID 类型种子数据（见 IDCommandService.registerIds 同一初始值）：
-- 将初始化从运行期注册前移到迁移，消除双 Broker 并发冷启动时
-- findById-then-insert 撞 uk_id 唯一键的竞态；运行期注册逻辑保留，
-- 作为未来新增 IDType 时的兜底。
-- 幂等要求：旧版本库已由 registerIds 写入这些类型并推进过计数器，
-- 无条件 INSERT 会撞 uk_id 唯一键阻断升级启动；而覆盖 current_id 会
-- 造成 ID 重复分配。只补缺失类型，已有行原样保留。
INSERT INTO `fluxion_id` (`type`, `current_id`, `step`)
VALUES ('WORKFLOW', 10000, 100),
       ('TRIGGER', 10000, 100),
       ('EXECUTION', 10000, 100),
       ('JOB', 10000, 100),
       ('APP', 10000, 100)
ON DUPLICATE KEY UPDATE `type` = `type`;