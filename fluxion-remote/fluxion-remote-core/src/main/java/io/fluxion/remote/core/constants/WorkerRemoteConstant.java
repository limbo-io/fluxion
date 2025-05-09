/*
 *
 *  * Copyright 2020-2024 Limbo Team (https://github.com/limbo-world).
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * 	http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package io.fluxion.remote.core.constants;

/**
 * @author Devil
 * @since 2023/8/16
 */
public interface WorkerRemoteConstant {
    /**
     * worker 心跳间隔
     */
    int HEARTBEAT_TIMEOUT_SECOND = 3;

    /**
     * 任务上报间隔 秒
     */
    int TASK_REPORT_SECONDS = 60;

    // ========== API Broker -> Worker ==========
    String API_JOB_DISPATCH = "/api/v1/job/dispatch";

    // ========== API Worker -> Worker ==========
    String API_TASK_DISPATCH = "/api/v1/task/dispatch";

    String API_TASK_REPORT = "/api/v1/task/report";
    // ========== API End ==========
}
