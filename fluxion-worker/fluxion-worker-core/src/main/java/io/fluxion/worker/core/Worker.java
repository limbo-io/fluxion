/*
 * Copyright 2020-2024 Limbo Team (https://github.com/limbo-io).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxion.worker.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.fluxion.common.constants.CommonConstants;

/**
 * Worker 行为方法定义
 *
 * @author Brozen
 * @since 2022-09-11
 */
public interface Worker {

    /**
     * 启动当前 Worker
     */
    void start();

    /**
     * 停止当前 Worker
     */
    void stop();

    /**
     * Worker的状态
     */
    enum Status {

        UNKNOWN(CommonConstants.UNKNOWN),

        /**
         * 闲置
         */
        IDLE("idle"),
        /**
         * 初始化中
         */
        INITIALIZING("initializing"),
        /**
         * Worker正常运行中
         */
        RUNNING("running"),

        /**
         * Worker熔断中，此状态的Worker无法接受作业，并将等待心跳重连并复活。
         */
        FUSING("fusing"),

        /**
         * 关闭中
         */
        TERMINATING("terminating"),

        /**
         * Worker已停止。
         */
        TERMINATED("terminated"),

        ;

        @JsonValue
        public final String status;

        Status(String status) {
            this.status = status;
        }

        public boolean is(String status) {
            return this.status.equals(status);
        }

        /**
         * 解析worker状态
         */
        @JsonCreator
        public static Status parse(String status) {
            for (Status statusEnum : values()) {
                if (statusEnum.is(status)) {
                    return statusEnum;
                }
            }
            return UNKNOWN;
        }

        public boolean isRunning() {
            return is(RUNNING.status);
        }

    }

}
