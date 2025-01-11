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

package io.fluxion.core.execution;

/**
 * 执行记录
 *
 * @author Devil
 */
public abstract class Execution {

    private State state;

    public abstract void execute();


    enum State {
        /**
         * 新创建
         */
        CREATED,
        /**
         * 运行中
         */
        RUNNING,
        /**
         * 暂停
         */
        PAUSED,
        /**
         * 重新启动
         */
        RESTARTED,
        /**
         * 关闭中
         */
        KILLING,
        /**
         * 成功
         */
        SUCCESS,
        /**
         * 失败
         */
        FAILED,
        /**
         * 手工终止
         */
        CANCELLED,
        /**
         * 队列中
         */
        QUEUED;

        public boolean isFinished() {
            return this == FAILED || this == SUCCESS || this ==  CANCELLED;
        }

        public boolean isCreated() {
            return this == CREATED || this == RESTARTED;
        }

        public boolean isRunning() {
            return this == RUNNING || this == KILLING;
        }

        public boolean isFailed() {
            return this == FAILED;
        }

        public boolean isPaused() {
            return this == PAUSED;
        }
    }

}
