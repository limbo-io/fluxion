/*
 * Copyright 2025-2030 fluxion-io Team (https://github.com/limbo-io).
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

package io.fluxion.test.integration.cluster.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Server API 客户端。真实 HTTP 调用，不直连 DB 或内部服务。
 * 业务响应按 Response{code,message,data} 封装校验，不只看 HTTP 状态。
 */
public class Api {

    public static final int SUCCESS_CODE = 200;
    public static final String SUCCESS_MESSAGE = "success";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final String base;

    public Api(int port) {
        this.base = "http://127.0.0.1:" + port;
    }

    /** POST JSON body 请求，返回 Response.data。 */
    public JsonNode post(String path, String jsonBody) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
            .build();
        return execute(request);
    }

    /** POST 带 query 参数（enable/disable/get），返回 Response.data。 */
    public JsonNode postQuery(String path, String query) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path + "?" + query))
            .timeout(Duration.ofSeconds(10))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
        return execute(request);
    }

    private JsonNode execute(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("http status " + response.statusCode()
                + " from " + request.uri() + " body: " + response.body());
        }
        JsonNode envelope = MAPPER.readTree(response.body());
        int code = envelope.path("code").asInt(-1);
        String message = envelope.path("message").asText("");
        if (code != SUCCESS_CODE) {
            throw new IOException("api error code=" + code + " message=" + message
                + " from " + request.uri());
        }
        return envelope.get("data");
    }

    // ---- 高层业务操作 ----

    public String createTrigger(String name, String description) throws IOException, InterruptedException {
        String body = MAPPER.createObjectNode()
            .put("name", name)
            .put("description", description).toString();
        return post("/api/v1/trigger/create", body).asText();
    }

    public void draft(String triggerId, JsonNode config) throws IOException, InterruptedException {
        post("/api/v1/trigger/draft", body(triggerId, config));
    }

    public void publish(String triggerId, JsonNode config) throws IOException, InterruptedException {
        post("/api/v1/trigger/publish", body(triggerId, config));
    }

    private static String body(String triggerId, JsonNode config) {
        return MAPPER.createObjectNode()
            .put("id", triggerId)
            .set("config", config).toString();
    }

    public void enable(String triggerId) throws IOException, InterruptedException {
        postQuery("/api/v1/trigger/enable", "id=" + urlencode(triggerId));
    }

    public void disable(String triggerId) throws IOException, InterruptedException {
        postQuery("/api/v1/trigger/disable", "id=" + urlencode(triggerId));
    }

    public JsonNode getTrigger(String triggerId) throws IOException, InterruptedException {
        return postQuery("/api/v1/trigger/get", "id=" + urlencode(triggerId));
    }

    public JsonNode workerPage() throws IOException, InterruptedException {
        return post("/api/v1/worker/page", MAPPER.createObjectNode()
            .put("current", 1)
            .put("pageSize", 20)
            .put("searchCount", false).toString());
    }

    public JsonNode observationOverview() throws IOException, InterruptedException {
        return post("/api/v1/observation/overview", "{}");
    }

    private static String urlencode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}