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

package io.fluxion.server.core.broker;

import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.remote.core.api.Response;
import io.fluxion.remote.core.api.request.WorkerRegisterRequest;
import io.fluxion.remote.core.constants.BrokerConstant;
import io.fluxion.remote.core.server.IHandleProcessor;
import io.fluxion.server.core.broker.converter.BrokerRpcConverter;
import io.fluxion.server.core.worker.cmd.WorkerRegisterCmd;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Devil
 */
@Slf4j
public class BrokerRpcHandleProcessor implements IHandleProcessor {

    @Override
    public Response<?> process(String path, String data) {
        switch (path) {
            case BrokerConstant.API_WORKER_REGISTER: {
                WorkerRegisterRequest request = JacksonUtils.toType(data, WorkerRegisterRequest.class);
                Cmd.send(new WorkerRegisterCmd(
                    request.getAppName(),
                    BrokerRpcConverter.toWorker(request)
                ));
                break;
            }
            case BrokerConstant.API_WORKER_HEARTBEAT: {
                break;
            }
        }
        String msg = "Invalid request, Path NotFound.";
        log.info("{} path={}", msg, path);
        return Response.builder().notFound(msg).build();
    }



}
