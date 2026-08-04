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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.fluxion.common.constants.CommonConstants;
import io.limbo.utils.time.Formatters;
import io.limbo.utils.time.LocalDateTimeUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * scheduleId + triggerAt 唯一
 *
 * @author Devil
 */
@Getter
public class ScheduleDelay {

    private final ID id;

    private final String delayId;

    private Status status;

    /**
     * Broker ID that owns the lease
     */
    private String leaseOwner;

    /**
     * Lease expiration time
     */
    private LocalDateTime leaseUntil;

    /**
     * Number of claim attempts
     */
    private Integer attempt;

    public void status(Status status) {
        this.status = status;
    }

    public void leaseOwner(String leaseOwner) {
        this.leaseOwner = leaseOwner;
    }

    public void leaseUntil(LocalDateTime leaseUntil) {
        this.leaseUntil = leaseUntil;
    }

    public void attempt(Integer attempt) {
        this.attempt = attempt;
    }

    public ScheduleDelay(ID id, Status status) {
        this.id = id;
        this.delayId = id.getScheduleId() + "_" + LocalDateTimeUtils.format(id.getTriggerAt(), Formatters.YMD_HMS_SSS);
        this.status = status;
    }

    @Getter
    @AllArgsConstructor
    public static class ID {
        /**
         * 关联调度
         */
        private String scheduleId;
        /**
         * 触发时间
         */
        private LocalDateTime triggerAt;

    }

    /**
     * Schedule delay 状态流转图
     * <pre>
     *                        ┌─────────────────────┐
     *     ┌──────────────────┤       UNKNOWN       │
     *     │                  │  (未知/初始化前状态)  │
     *     │                  └──────────┬──────────┘
     *     │                             │
     *     │                             │ 创建
     *     │                             ▼
     *     │  ┌──────────────────────────────────────────────────────┐
     *     │  │                                                      │
     *     │  │                    ┌─────────────┐                   │
     *     │  │                    │    INIT     │◄───────────────────┤ 版本变更
     *     │  │                    │  (刚创建)   │                   │ 回退/重试
     *     │  │                    └──────┬──────┘                   │
     *     │  │                           │ claim                   │
     *     │  │                           │ (被Broker认领)           │
     *     │  │                           ▼                         │
     *     │  │                  ┌─────────────────┐                 │
     *     │  │    claim expire │    CLAIMED      │ execute         │
     *     │  └────────────────┤ (已被认领待执行) │─────────────────┘
     *     │                   │  leaseOwner     │
     *     │                   │  leaseUntil     │
     *     │                   └────────┬────────┘
     *     │                            │ executeToken
     *     │                            │ (开始执行)
     *     │                            ▼
     *     │                     ┌─────────────┐
     *     │                     │   RUNNING   │
     *     │                     │  (运行中)    │
     *     │                     │ execution   │
     *     │                     │   Token     │
     *     │                     └──────┬──────┘
     *     │                            │
     *     │         ┌──────────────────┴──────────────────┐
     *     │         │                                   │
     *     │         │ success                           │ fail
     *     │         ▼                                   ▼
     *     │  ┌─────────────┐                     ┌─────────────┐
     *     │  │  SUCCEED    │                     │   FAILED    │
     *     │  │   (完成)    │                     │  (执行失败)  │
     *     │  └─────────────┘                     └──────┬──────┘
     *     │                                               │
     *     │                    ┌──────────────────────────┘
     *     │                    │ retry (per policy)
     *     └────────────────────┘
     *
     * 关键流转说明:
     * 1. INIT → CLAIMED: Broker 通过 lease 机制认领任务
     * 2. CLAIMED → INIT: 续约失败或 lease 过期退回 (重新竞争)
     * 3. CLAIMED → RUNNING: 任务触发执行，生成 executionToken
     * 4. RUNNING → SUCCEED/FAILED: 执行完成 (需验证 executionToken)
     * 5. INIT → INVALID: 版本变更导致任务失效 (不执行)
     * </pre>
     */
    public enum Status {
        UNKNOWN(CommonConstants.UNKNOWN),
        /**
         * 刚创建
         */
        INIT("init"),
        /**
         * 已被Broker认领，等待触发执行
         * <p>
         * 流转至: RUNNING (执行) 或 INIT (lease过期/续约失败)
         */
        CLAIMED("claimed"),
        /**
         * 运行中
         * <p>
         * 流转至: SUCCEED (执行成功) 或 FAILED (执行失败)
         */
        RUNNING("running"),
        /**
         * 完成
         */
        SUCCEED("succeed"),
        /**
         * 执行失败
         * <p>
         * 可能触发: retry (按 retry policy 重试) 进入 INIT
         */
        FAILED("failed"),
        /**
         * 无效 不执行 可能是版本变更导致
         */
        INVALID("invalid"),
        ;

        @JsonValue
        public final String value;


        Status(String type) {
            this.value = type;
        }

        public boolean is(String type) {
            return this.value.equals(type);
        }

        @JsonCreator
        public static Status parse(String value) {
            for (Status v : values()) {
                if (v.is(value)) {
                    return v;
                }
            }
            return UNKNOWN;
        }
    }
}
