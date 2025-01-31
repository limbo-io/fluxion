/*
 * Copyright 2024-2030 fluxion-io Team (https://github.com/fluxion-io).
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

package io.fluxion.remote.core.api.request;

import io.fluxion.remote.core.api.Request;
import io.fluxion.remote.core.api.dto.SystemInfoDTO;
import io.fluxion.remote.core.api.dto.WorkerExecutorDTO;
import io.fluxion.remote.core.api.dto.WorkerMetricDTO;
import io.fluxion.remote.core.api.dto.WorkerTagDTO;
import io.fluxion.remote.core.api.response.WorkerRegisterResponse;
import io.fluxion.remote.core.constants.Protocol;

import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * @author Devil
 * @since 2023/8/3
 */
public class WorkerRegisterRequest implements Request<WorkerRegisterResponse> {

    /**
     * 应用名称
     */
    private String appName;

    /**
     * @see Protocol
     */
    private String protocol;

    private String host;

    private int port;

    /**
     * 可用资源
     */
    private WorkerMetricDTO metric;

    /**
     * worker 的标签
     */
    private List<WorkerTagDTO> tags;

    /**
     * 执行器
     */
    @NotEmpty(message = "worker executor can't be empty")
    private List<WorkerExecutorDTO> executors;
}
