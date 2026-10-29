/*
 * Copyright 2025-2030 limbo-io Team (https://github.com/limbo-io).
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

package io.fluxion.test.unit.common;

import com.fasterxml.jackson.databind.JavaType;
import io.fluxion.remote.core.api.Response;
import io.fluxion.remote.core.api.request.broker.WorkerRegisterRequest;
import io.fluxion.remote.core.api.response.broker.WorkerRegisterResponse;
import io.fluxion.server.core.workflow.query.WorkflowByIdQuery;
import io.limbo.utils.ReflectionUtils;
import io.limbo.utils.json.JacksonUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReflectionTest {

    @Test
    void shouldResolveResponseTypesForRpcAndCqrsRequests() {
        assertThat(ReflectionUtils.refType(new WorkerRegisterRequest())).isEqualTo(WorkerRegisterResponse.class);
        assertThat(ReflectionUtils.refType(new WorkflowByIdQuery("workflow-1")))
            .isEqualTo(WorkflowByIdQuery.Response.class);
    }

    @Test
    void shouldDeserializeTypedWorkerRegistrationResponse() {
        String json = "{\"code\":200,\"message\":\"success\",\"data\":{\"appId\":\"10001\","
            + "\"workerId\":\"worker-1\",\"broker\":{\"protocol\":\"http\",\"host\":\"127.0.0.1\",\"port\":9785}}}";
        // 与 RPC 客户端相同：从请求泛型构造响应类型，防止 data 退化成 Map。
        JavaType type = JacksonUtils.MAPPER.getTypeFactory().constructParametricType(
            Response.class, ReflectionUtils.refType(new WorkerRegisterRequest()));
        Response<WorkerRegisterResponse> response = JacksonUtils.toType(json, type);
        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isInstanceOf(WorkerRegisterResponse.class);
        assertThat(response.getData().getAppId()).isEqualTo("10001");
        assertThat(response.getData().getWorkerId()).isEqualTo("worker-1");
        assertThat(response.getData().getBroker().getHost()).isEqualTo("127.0.0.1");
        assertThat(response.getData().getBroker().getPort()).isEqualTo(9785);
    }
}
