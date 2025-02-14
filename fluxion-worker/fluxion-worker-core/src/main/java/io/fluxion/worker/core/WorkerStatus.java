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

package io.fluxion.worker.core;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author PengQ
 * @since 0.0.1
 */
public class WorkerStatus {

    private final AtomicReference<S> status;


    public WorkerStatus() {
        this.status = new AtomicReference<>(S.IDLE);
    }

    public boolean isRunning() {
        return status.get() == S.RUNNING;
    }

    public boolean change(WorkerStatus.S expect, WorkerStatus.S update) {
        return status.compareAndSet(expect, update);
    }

    public S status() {
        return status.get();
    }

    public enum S {

        /**
         * 闲置
         */
        IDLE,
        /**
         * 初始化中
         */
        INITIALIZING,
        /**
         * 运行中
         */
        RUNNING,
        /**
         * 关闭中
         */
        TERMINATING,
        /**
         * 已经关闭
         */
        TERMINATED,
    }
}
