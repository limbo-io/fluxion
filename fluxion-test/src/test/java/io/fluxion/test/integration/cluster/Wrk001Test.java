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

package io.fluxion.test.integration.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fluxion.test.integration.cluster.support.Await;
import io.fluxion.test.integration.cluster.support.Cluster;
import io.fluxion.test.integration.cluster.support.RunReport;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** WRK-001 本地链路：真实触发 → 业务阻塞 → Worker 强退 → 存活 Worker 重试 → 终态。 */
class Wrk001Test extends ClusterTestSupport {

    @Test
    void wrk001_worker_exit_recovers_same_job() throws Exception {
        String runId = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC).format(Instant.now()) + "-WRK-001";
        RunReport report = new RunReport(runId, "WRK-001", "default", Path.of("target", "cluster-e2e"));
        recordGit(report);
        Cluster cluster = null;
        String stage = "environment-check";
        try {
            checkPodman();
            buildBrokerJar();
            Path jar = locateJar(Path.of("..", "fluxion-server", "fluxion-server-start", "target"),
                "fluxion-server-start-*-cluster-e2e.jar", BROKER_JAR_HINT);
            stage = "cluster-startup";
            cluster = new Cluster(runId, report.runRoot, true);
            cluster.start(jar);
            report.stageMarksMs.putAll(cluster.stageMarksMs());
            Cluster running = cluster;

            stage = "api-prepare";
            var api = cluster.brokerApi(0);
            var workers = api.workerPage();
            String appId = workers.get(0).path("appId").asText();
            if (appId.isBlank() || workers.size() != 2
                || !appId.equals(workers.get(1).path("appId").asText())) {
                throw new IllegalStateException("expected two workers in the same non-empty app");
            }
            String triggerId = api.createTrigger("wrk001-" + runId, "worker failure recovery");
            ObjectNode config = config(appId);
            report.snapshot("config", config);
            api.draft(triggerId, config);
            api.publish(triggerId, config);
            api.enable(triggerId);
            String version = api.getTrigger(triggerId).path("version").asText();
            report.mark(stage);

            stage = "business-blocked";
            Await.until(stage, Duration.ofMinutes(22), Duration.ofSeconds(1),
                () -> running.events().events().stream().anyMatch(e -> Boolean.TRUE.equals(e.get("blocked"))));
            var first = cluster.events().events().stream()
                .filter(e -> Boolean.TRUE.equals(e.get("blocked"))).findFirst().orElseThrow();
            String jobId = first.get("jobId").toString();
            String failedWorker = first.get("workerTag").toString();
            var before = job(cluster, jobId);
            String executionId = before.get("execution_id").toString();
            report.snapshot("job-before-kill", before);
            report.snapshot("first-business-start", first);
            require(report, "fault-window", "RUNNING without BUSINESS_FINISH", before.get("status"),
                "running".equalsIgnoreCase(String.valueOf(before.get("status")))
                    && cluster.events().eventsOfJob(jobId).stream()
                        .noneMatch(e -> "BUSINESS_FINISH".equals(e.get("type"))));
            require(report, "fault-worker-address", "event worker matches dispatch", before.get("worker_address"),
                ("http://127.0.0.1:" + cluster.workerPorts().get(failedWorker))
                    .equals(before.get("worker_address")));
            report.mark(stage);

            stage = "worker-kill";
            long killedAt = System.currentTimeMillis();
            long pid = cluster.killWorker(failedWorker);
            report.snapshot("fault", Map.of("worker", failedWorker, "pid", pid, "at", killedAt,
                "exitConfirmed", true, "jobId", jobId, "executionId", executionId,
                "dispatchAttempt", before.get("dispatch_attempt")));
            report.mark(stage);

            stage = "worker-offline";
            int failedPort = cluster.workerPorts().get(failedWorker);
            Await.until(stage, Duration.ofSeconds(60), Duration.ofSeconds(1), () -> {
                try {
                    return running.db().query("select status from fluxion_worker where port = ?", failedPort)
                        .stream().anyMatch(w -> "offline".equalsIgnoreCase(String.valueOf(w.get("status"))));
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            });
            report.snapshot("offline-worker", cluster.db().query(
                "select * from fluxion_worker where port = ?", failedPort));
            report.mark(stage);

            stage = "recovery-convergence";
            // 预算包含失联扫描、重试扫描、派发、业务执行与结果上报。
            Await.until(stage, Duration.ofSeconds(150), Duration.ofSeconds(1), () -> {
                var current = job(running, jobId);
                return "succeed".equalsIgnoreCase(String.valueOf(current.get("status")))
                    && executionSucceeded(running, executionId);
            });
            long recoveredAt = System.currentTimeMillis();
            api.disable(triggerId);
            var after = job(cluster, jobId);
            var jobs = cluster.db().query("select * from fluxion_job where execution_id = ?", executionId);
            var executions = cluster.db().query("select * from fluxion_execution where execution_id = ?", executionId);
            var events = cluster.events().eventsOfJob(jobId);
            report.snapshot("jobs-after-recovery", jobs);
            report.snapshot("execution-after-recovery", executions);
            report.snapshot("recovery", Map.of("at", recoveredAt, "elapsedMs", recoveredAt - killedAt,
                "workerAddress", after.get("worker_address")));
            report.mark(stage);

            stage = "assertions";
            require(report, "stable-job-identity", "one original job", jobs,
                jobs.size() == 1 && jobId.equals(jobs.get(0).get("job_id")));
            require(report, "retry-times", 1, after.get("retry_times"), number(after.get("retry_times")) == 1);
            require(report, "new-dispatch-attempt", "greater than original", after.get("dispatch_attempt"),
                number(after.get("dispatch_attempt")) > number(before.get("dispatch_attempt")));
            require(report, "version-preserved", version, executions,
                !version.isBlank() && executions.size() == 1
                    && version.equals(executions.get(0).get("executable_version")));
            var starts = events.stream().filter(e -> "BUSINESS_START".equals(e.get("type"))).toList();
            var finishes = events.stream().filter(e -> "BUSINESS_FINISH".equals(e.get("type"))).toList();
            require(report, "business-attempt-count", "2 starts, 1 finish", events,
                starts.size() == 2 && finishes.size() == 1);
            var finish = finishes.get(0);
            String survivor = finish.get("workerTag").toString();
            require(report, "surviving-worker-completed", "new worker matches dispatch", finish,
                !failedWorker.equals(survivor)
                    && ("http://127.0.0.1:" + cluster.workerPorts().get(survivor)).equals(after.get("worker_address"))
                    && survivor.equals(starts.get(1).get("workerTag"))
                    && Cluster.EXECUTOR_NAME.equals(finish.get("executor"))
                    && "fluxion-cluster-e2e-ok".equals(finish.get("output")));
            require(report, "other-nodes-alive", "2 brokers and surviving worker alive", cluster.nodeEvidence(),
                cluster.nodeEvidence().stream().filter(n -> Boolean.TRUE.equals(n.get("aliveAtEnd"))).count() == 3);
            report.mark(stage);
        } catch (Throwable failure) {
            report.status = stage.equals("environment-check") || stage.equals("cluster-startup") ? "ERROR"
                : failure.getMessage() != null && failure.getMessage().startsWith("timeout waiting") ? "TIMEOUT" : "FAIL";
            report.failureStage = stage;
            report.errorMessage = failure.toString();
            try {
                finish(report, cluster);
            } catch (Throwable cleanup) {
                failure.addSuppressed(cleanup);
            }
            rethrow(failure);
        }
        finish(report, cluster);
        if (!"PASS".equals(report.status)) {
            throw new AssertionError(report.errorMessage);
        }
    }

    private static ObjectNode config(String appId) {
        ObjectNode config = new ObjectMapper().createObjectNode();
        config.put("type", "schedule");
        ObjectNode schedule = config.putObject("scheduleOption");
        schedule.put("type", "fixed_rate");
        schedule.put("startTime", LocalDateTime.ofInstant(Instant.now().plus(Duration.ofMinutes(6)),
            ZoneOffset.UTC).withNano(0).toString());
        schedule.put("timeZone", "UTC");
        schedule.put("scheduleInterval", "PT30S");
        schedule.put("misfirePolicy", "FIRE_RETRY");
        schedule.put("maxFireAttempts", 1);
        schedule.put("recoveryEnabled", false);
        ObjectNode execute = config.putObject("executeConfig");
        execute.put("type", "executor");
        ObjectNode retry = execute.putObject("retryOption");
        retry.put("retryTimes", 1);
        retry.put("retryInterval", 1);
        retry.put("retryType", "all");
        ObjectNode executor = execute.putObject("executor");
        executor.put("type", "custom");
        executor.put("name", Cluster.EXECUTOR_NAME);
        executor.put("appId", appId);
        executor.put("executeMode", "standalone");
        return config;
    }

    private static Map<String, Object> job(Cluster cluster, String jobId) {
        try {
            return cluster.db().query("select * from fluxion_job where job_id = ?", jobId).get(0);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean executionSucceeded(Cluster cluster, String executionId) {
        try {
            return cluster.db().query("select status from fluxion_execution where execution_id = ?", executionId)
                .stream().anyMatch(e -> "succeed".equalsIgnoreCase(String.valueOf(e.get("status"))));
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long number(Object value) {
        return Long.parseLong(String.valueOf(value));
    }

    private static void require(RunReport report, String name, Object expected, Object actual, boolean pass) {
        report.record(name, expected, actual, pass);
        if (!pass) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }
}
