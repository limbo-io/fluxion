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

package io.fluxion.remote.core.constants;

/**
 * @author PengQ
 * @since 0.0.1
 */
public interface BrokerRemoteConstant {
    // ========== API ==========
    String API_WORKER_REGISTER = "/api/v1/worker/register";

    String API_WORKER_HEARTBEAT = "/api/v1/worker/heartbeat";

    String API_BROKER_PING = "/api/v1/broker/ping";

    String API_TASK_DISPATCHED = "/api/v1/task/dispatched";

    String API_TASK_START = "/api/v1/task/start";

    String API_TASK_REPORT = "/api/v1/task/report";

    String API_TASK_SUCCESS = "/api/v1/task/success";

    String API_TASK_FAIL = "/api/v1/task/fail";
    // ========== API ==========
}
