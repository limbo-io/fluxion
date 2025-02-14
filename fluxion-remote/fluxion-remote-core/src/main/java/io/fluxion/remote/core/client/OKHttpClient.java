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

package io.fluxion.remote.core.client;

import com.google.common.net.HttpHeaders;
import io.fluxion.common.utils.ReflectionUtils;
import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.remote.core.api.Request;
import io.fluxion.remote.core.constants.Protocol;
import io.fluxion.remote.core.exception.RpcException;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;

/**
 * @author Devil
 */
public class OKHttpClient implements Client {

    private static final Logger log = LoggerFactory.getLogger(OKHttpClient.class);

    private final OkHttpClient client;

    // application/json; charset=utf-8
    private static final String JSON_UTF_8 = com.google.common.net.MediaType.JSON_UTF_8.toString();

    private static final MediaType MEDIA_TYPE = MediaType.parse(JSON_UTF_8);

    public OKHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        this.client = builder.build();
    }

    @Override
    public <R> R call(URL url, Request<R> request) {
        try {
            ResponseBody responseBody = executePost(url, request);
            Class<R> responseType = ReflectionUtils.refType(request);
            return JacksonUtils.toType(responseBody.string(), responseType);
        } catch (IOException e) {
            throw new RpcException("Api access failed " + logRequest(url, JacksonUtils.toJSONString(request)), e);
        }
    }

    @Override
    public Protocol protocol() {
        return Protocol.HTTP;
    }

    /**
     * 通过 OkHttp 执行请求，并获取响应
     */
    protected ResponseBody executePost(URL url, Object param) {
        String json = "";
        if (param != null) {
            json = JacksonUtils.toJSONString(param);
        }
        RequestBody body = RequestBody.create(json, MEDIA_TYPE);

        okhttp3.Request request = new okhttp3.Request.Builder()
            .url(url)
            .header(HttpHeaders.CONTENT_TYPE, JSON_UTF_8)
            .post(body)
            .build();
        Call call = client.newCall(request);

        if (log.isDebugEnabled()) {
            log.debug("call api {}", logRequest(url, json));
        }

        try {
            // HTTP 响应状态异常
            Response response = call.execute();
            if (!response.isSuccessful()) {
                throw new RpcException("Api access failed; " + logRequest(url, json) + " code=" + response.code());
            }

            // 无响应 body 是异常
            if (response.body() == null) {
                throw new RpcException("Api response empty body " + logRequest(url, json));
            }
            return response.body();
        } catch (IOException e) {
            throw new RpcException("Api access failed " + logRequest(url, json), e);
        }
    }

    private String logRequest(URL url, String param) {
        return String.format("request[url=%s, param=%s]", url.toString(), param);
    }

}
