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
import io.fluxion.remote.core.api.dto.WorkerExecutorDTO;
import io.fluxion.remote.core.api.dto.WorkerResourceDTO;
import io.fluxion.remote.core.api.dto.WorkerTagDTO;
import io.fluxion.remote.core.api.response.WorkerRegisterResponse;
import io.fluxion.remote.core.constants.Protocol;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.net.URL;
import java.util.List;

/**
 * @author Devil
 * @since 2023/8/3
 */
public class WorkerRegisterRequest implements Request<WorkerRegisterResponse> {

    /**
     * 注册时指定的 worker name
     */
    private String name;

    /**
     * 通信使用的 URL。
     *
     * @see Protocol 需要使用指定类型的协议
     */
    @NotNull(message = "Worker URL can't be null")
    private URL url;

    /**
     * 可用资源
     */
    private WorkerResourceDTO availableResource;

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
