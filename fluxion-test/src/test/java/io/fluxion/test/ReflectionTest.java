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

package io.fluxion.test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import io.fluxion.common.utils.ReflectionUtils;
import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.remote.core.api.Request;
import io.fluxion.remote.core.api.Response;
import io.fluxion.remote.core.api.request.broker.WorkerRegisterRequest;
import io.fluxion.remote.core.api.response.broker.WorkerRegisterResponse;
import io.fluxion.server.core.flow.query.FlowByIdQuery;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;

/**
 * @author Devil
 */
public class ReflectionTest {

    @Test
    void test() {
        WorkerRegisterRequest request = new WorkerRegisterRequest();
        Class<io.fluxion.remote.core.api.Response<WorkerRegisterResponse>> responseType = ReflectionUtils.refType(request);
        System.out.println(responseType);

        FlowByIdQuery query = new FlowByIdQuery("");
        Class<FlowByIdQuery.Response> queryResponseType = ReflectionUtils.refType(query);
        System.out.println(queryResponseType);

        Type superclass = Response.r(WorkerRegisterResponse.class);

//        Response<WorkerRegisterResponse> responseT = Response.ok(new WorkerRegisterResponse());
//        Type actualTypeArgument = ((ParameterizedType) responseT.getClass().getGenericInterfaces()[0]).getActualTypeArguments()[0];


        String json = "{\"code\":200,\"message\":\"success\",\"data\":{\"appId\":\"10001\",\"workerId\":\"e3830f999cc499cff647901aaa903d48\",\"broker\":{\"protocol\":\"http\",\"host\":\"198.19.249.3\",\"port\":9785},\"brokerTopology\":{\"brokers\":[{\"protocol\":\"http\",\"host\":\"198.19.249.3\",\"port\":9785}]}}}";
        TypeReference<Response<WorkerRegisterResponse>> typeReference = new TypeReference<Response<WorkerRegisterResponse>>() {
        };
        Response<WorkerRegisterResponse> response = JacksonUtils.toType(json, typeReference);
//        Response<WorkerRegisterRequest> response = JacksonUtils.toType(json, new TypeReference<Response<WorkerRegisterRequest>>() {
//        });
        System.out.println(response);

        WorkerRegisterRequest request1 = new WorkerRegisterRequest();
        Response<WorkerRegisterResponse> r = call2(json, request1);
        System.out.println(JacksonUtils.toJSONString(r));
    }

    public <R> io.fluxion.remote.core.api.Response<R> call(String json, Request<R> request) {
        TypeReference<Response<R>> typeReference = new TypeReference<io.fluxion.remote.core.api.Response<R>>() {
        };
        return JacksonUtils.toType(json, typeReference);
    }

    public <R> io.fluxion.remote.core.api.Response<R> call2(String json, Request<R> request) {
        Class<R> objectClass = ReflectionUtils.refType(request);
        JavaType responseType = JacksonUtils.MAPPER.getTypeFactory()
            .constructParametricType(Response.class, objectClass);
        return JacksonUtils.toType(json, responseType);
    }
}
