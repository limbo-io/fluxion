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
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 测试进程内嵌事件接收器：接收 Worker 测试执行器上报的业务事件，
 * 不依赖可能被杀死的 Worker 进程内存。runId 不匹配的事件直接丢弃。
 */
public class EventReceiver implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpServer server;
    private final String runId;
    /** 接收时间附在事件上，作为独立证据。 */
    private final List<Map<String, Object>> events = new CopyOnWriteArrayList<>();
    private final Map<String, List<Map<String, Object>>> byJob = new ConcurrentHashMap<>();

    private final AtomicBoolean blockFirstStart;

    public EventReceiver(String runId) throws IOException {
        this(runId, false);
    }

    public EventReceiver(String runId, boolean blockFirstStart) throws IOException {
        this.blockFirstStart = new AtomicBoolean(blockFirstStart);
        this.runId = runId;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 64);
        this.server.createContext("/event", this::handle);
        this.server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String url() {
        return "http://127.0.0.1:" + port() + "/event";
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "method not allowed");
                return;
            }
            JsonNode event = MAPPER.readTree(exchange.getRequestBody().readAllBytes());
            Map<String, Object> record = new java.util.LinkedHashMap<>();
            record.put("receivedAt", System.currentTimeMillis());
            record.put("runId", event.path("runId").asText(""));
            record.put("workerTag", event.path("workerTag").asText(""));
            record.put("type", event.path("type").asText(""));
            record.put("executor", event.path("executor").asText(""));
            record.put("output", event.path("output").asText(""));
            record.put("jobId", event.path("jobId").asText(""));
            record.put("taskId", event.path("taskId").asText(""));
            record.put("at", event.path("at").asLong(0));
            boolean block = runId.equals(record.get("runId"))
                && "BUSINESS_START".equals(record.get("type"))
                && blockFirstStart.compareAndSet(true, false);
            record.put("blocked", block);
            if (runId.equals(record.get("runId"))) {
                events.add(record);
                byJob.computeIfAbsent((String) record.get("jobId"), k -> new CopyOnWriteArrayList<>()).add(record);
            }
            respond(exchange, 200, block ? "block" : "ok");
        } catch (Exception e) {
            respond(exchange, 400, String.valueOf(e));
        }
    }

    private void respond(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** 事件接收确认后才允许结束场景：全量事件（只读副本）。 */
    public List<Map<String, Object>> events() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    public List<Map<String, Object>> eventsOfJob(String jobId) {
        List<Map<String, Object>> list = byJob.get(jobId);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(list));
    }

    @Override
    public void close() {
        server.stop(0);
    }
}