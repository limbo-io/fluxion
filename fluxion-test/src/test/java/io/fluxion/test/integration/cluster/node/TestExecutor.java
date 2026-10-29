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

package io.fluxion.test.integration.cluster.node;

import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.task.TaskContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * 集群 E2E 测试执行器：以固定业务输出运行，并向测试进程上报业务开始/完成事件。
 *
 * <p>事件通道为测试装配，不修改业务 Executor 接口：上报地址、runId、worker 标签均由
 * 节点进程的 -D 系统属性注入。事件通道失败不改变业务结果（由测试端按缺失证据判失败）。</p>
 */
@Component
public class TestExecutor implements Executor {

    private static final Logger log = LoggerFactory.getLogger(TestExecutor.class);

    /** 固定业务输出，测试端据此断言实际执行器身份。 */
    static final String OUTPUT = "fluxion-cluster-e2e-ok";

    private static final int REPORT_ATTEMPTS = 3;

    @Override
    public void run(TaskContext context) {
        if (report("BUSINESS_START", context)) {
            try {
                // 控制通道只阻塞首个 attempt，测试进程确认事件后强杀该 Worker。
                Thread.sleep(300_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("business control interrupted", e);
            }
            throw new IllegalStateException("business control timed out before worker kill");
        }
        try {
            // 业务动作即固定输出本身，无其他副作用
            report("BUSINESS_FINISH", context);
        } catch (Exception e) {
            // 上报失败不影响业务结果，测试端以证据缺失判失败
            log.warn("event report failed for job {}", context.getJobId(), e);
        }
    }

    private boolean report(String type, TaskContext context) {
        String url = System.getProperty("e2e.event.url", "");
        if (url.isEmpty()) {
            log.warn("e2e.event.url not set; event {} for job {} dropped", type, context.getJobId());
            return false;
        }
        String payload = "{\"runId\":\"" + System.getProperty("e2e.run.id", "") + "\""
            + ",\"workerTag\":\"" + System.getProperty("e2e.worker.tag", "") + "\""
            + ",\"type\":\"" + type + "\""
            + ",\"executor\":\"" + getClass().getName() + "\""
            + ",\"output\":\"" + OUTPUT + "\""
            + ",\"jobId\":\"" + context.getJobId() + "\""
            + ",\"taskId\":\"" + context.getId() + "\""
            + ",\"at\":" + Instant.now().toEpochMilli() + "}";
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        for (int attempt = 1; attempt <= REPORT_ATTEMPTS; attempt++) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(2_000);
                conn.setReadTimeout(2_000);
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body);
                }
                int code = conn.getResponseCode();
                try {
                    if (code == 200) {
                        try (var input = conn.getInputStream()) {
                            return "block".equals(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                        }
                    }
                } finally {
                    conn.disconnect();
                }
            } catch (IOException e) {
                log.warn("event report attempt {} failed for job {} ({}): {}",
                    attempt, context.getJobId(), type, e.getMessage());
            }
            try {
                Thread.sleep(500L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}