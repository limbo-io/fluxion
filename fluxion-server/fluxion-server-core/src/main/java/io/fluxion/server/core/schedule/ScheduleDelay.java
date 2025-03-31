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
import io.fluxion.common.utils.time.Formatters;
import io.fluxion.common.utils.time.LocalDateTimeUtils;
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

    public void status(Status status) {
        this.status = status;
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

    public enum Status {
        UNKNOWN(CommonConstants.UNKNOWN),
        /**
         * 刚创建
         */
        INIT("init"),
        /**
         * 运行中
         */
        RUNNING("running"),
        /**
         * 完成
         */
        SUCCEED("succeed"),
        /**
         * 执行失败
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
