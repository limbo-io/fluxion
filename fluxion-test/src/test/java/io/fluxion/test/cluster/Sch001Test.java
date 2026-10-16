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

package io.fluxion.test.cluster;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fluxion.test.cluster.support.Api;
import io.fluxion.test.cluster.support.Cluster;
import io.fluxion.test.cluster.support.RunReport;
import io.fluxion.test.cluster.support.Shell;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCH-001：固定速率真实触发并完成。
 *
 * <p>隔离 2 Broker + 2 Worker + Podman MySQL；通过真实 API 创建、草稿、发布、启用 Trigger，
 * 观测两个连续计划触发点的预生成、真实触发、派发执行、上报收敛，随后禁用并做目标实例断言。
 * 产物与清理遵循实现与验收约定：失败也先归档再清理，清理只作用于本场景资源。</p>
 */
class Sch001Test {

    /** FIXED_RATE 周期：30 秒（目标点 t1、t2=t1+30s）。 */
    private static final Duration TRIGGER_INTERVAL = Duration.ofSeconds(30);

    /**
     * 目标首点偏移：调度扫描窗口要求 startTime ≤ now-5min 才可被拾取，
     * startTime 必须落在未来（本次启用的 ≥6min 即可）；实际目标点在预生成后选取。
     */
    private static final Duration FIRST_POINT_AFTER_ENABLE = Duration.ofMinutes(6);

    /**
     * 预生成等待预算：拾取资格 startTime+5min，扫描周期 5min 但栅格锚点为 Broker 启动时刻，
     * 必须覆盖一个完整扫描周期；13min 实测漏扫，扩至 20min 保证必中至少一次扫描。
     */
    private static final Duration PRE_GENERATION_WAIT = Duration.ofMinutes(20);

    /** 执行收敛预算：ExecutionLoader 60s 加载周期 + 派发/执行/上报/反馈余量。 */
    private static final Duration CONVERGE_BUDGET = Duration.ofSeconds(150);

    /** 目标点匹配容忍：datetime(3) 存储与调度计算之间的毫秒差。 */
    private static final long TIME_TOLERANCE_MS = 1_000;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void sch001_fixed_rate_trigger_twice() {
        String runId = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .format(LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)) + "-SCH-001";

        RunReport report;
        try {
            report = new RunReport(runId, "SCH-001", "default", Path.of("target", "cluster-e2e"));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create run dir", e);
        }
        recordGit(report);

        Cluster cluster = null;
        String stage = "environment-check";
        try {
            checkPodman();
            // 实现与验收约定：节点代码必须来自本次 reactor 构建。
            // 嵌套触发 package（含上游 jar）后再 seal fat jar，禁止复用旧界尺寸不完整的 jar。
            buildBrokerJar();
            Path brokerJar = locateJar(Path.of("..", "fluxion-server", "fluxion-server-start", "target"),
                "fluxion-server-start-*-cluster-e2e.jar", BROKER_JAR_HINT);
            report.manifestData.put("brokerJar", brokerJar.toString());
            report.mark(stage);

            stage = "cluster-startup";
            cluster = new Cluster(runId, report.runRoot);
            cluster.start(brokerJar);
            report.stageMarksMs.putAll(cluster.stageMarksMs());

            stage = "api-prepare";
            // 派发过滻 Worker 按 App：appId 必须取自真实 Worker 注册产物
            //（findByAppIdAndStatusIn 条件查询，不可为空）
            String appId = requireWorkersAppId(cluster);
            // startTime 只需足够未来：调度扫描在 startTime 后 ≥5min 才 MAY 拾取
            //（查询窗口要求 startTime <= now-5min），此前的点会被遗漏触发恢复按默认丢弃；
            // 目标点改为在预生成后从实际 Future 点集中选取（见 pre-generation 阶段）
            long startTimeMs = alignedSecond(Instant.now().plus(FIRST_POINT_AFTER_ENABLE));
            String triggerId = createAndEnableTrigger(cluster, runId, startTimeMs, appId);
            snapshotScheduleState(report, cluster, triggerId);
            JsonNode trigger = cluster.brokerApi(0).getTrigger(triggerId);
            report.record("trigger-published-and-enabled", "published=true, enabled=true",
                "published=" + trigger.path("published").asText() + ", enabled=" + trigger.path("enabled").asText(),
                trigger.path("published").asBoolean() && trigger.path("enabled").asBoolean());
            report.record("trigger-version-assigned", "non-empty version",
                "version=" + trigger.path("version").asText(),
                !trigger.path("version").asText("").isBlank());
            report.mark(stage);

            stage = "pre-generation";
            // 等待首次扫描拾取并预生成，从实际 Future 点集中选取最早两个点作为目标
            long[] targets = selectTwoFuturePoints(cluster, report, triggerId);
            long t1Ms = targets[0];
            long t2Ms = targets[1];
            report.mark(stage);

            stage = "triggering-and-convergence";
            waitExecutionSucceed(cluster, triggerId, t1Ms);
            waitExecutionSucceed(cluster, triggerId, t2Ms);
            report.mark(stage);

            stage = "disable-trigger";
            cluster.brokerApi(0).disable(triggerId);
            JsonNode after = cluster.brokerApi(0).getTrigger(triggerId);
            report.record("trigger-disabled-after-scenario", "enabled=false",
                "enabled=" + after.path("enabled").asText(), !after.path("enabled").asBoolean());
            report.mark(stage);

            stage = "evidence-snapshot";
            List<Map<String, Object>> executions = cluster.db().query(
                "select * from fluxion_execution where trigger_id = ? and is_deleted = 0 order by trigger_at",
                triggerId);
            List<Map<String, Object>> jobs = cluster.db().query(
                "select * from fluxion_job where execution_id in "
                    + "(select execution_id from fluxion_execution where trigger_id = ? and is_deleted = 0)"
                    + " and is_deleted = 0 order by trigger_at",
                triggerId);
            List<Map<String, Object>> versions = cluster.db().query(
                "select * from fluxion_version where ref_id = ? order by created_at", triggerId);
            report.snapshot("executions", executions);
            report.snapshot("jobs", jobs);
            report.snapshot("trigger-versions", versions);
            report.snapshot("schedule", cluster.db().query(
                "select * from fluxion_schedule where schedule_id = ?", triggerId));
            report.snapshot("brokers", cluster.db().query(
                "select * from fluxion_broker where is_deleted = 0"));
            report.snapshot("workers", cluster.db().query(
                "select * from fluxion_worker where is_deleted = 0"));
            report.mark(stage);

            stage = "assertions";
            assertScenario(report, cluster, t1Ms, t2Ms, executions, jobs);
            report.mark(stage);
        } catch (Throwable t) {
            report.status = switch (stage) {
                case "environment-check", "cluster-startup" -> "ERROR";
                case "pre-generation", "triggering-and-convergence" -> "TIMEOUT";
                default -> "FAIL";
            };
            report.failureStage = stage;
            report.errorMessage = t.getMessage() == null ? String.valueOf(t) : t.getMessage();
            // finish 自身失败（如 results 写不进去）作为附加错误保留，不掩盖原始失败
            try {
                finish(report, cluster);
            } catch (Throwable second) {
                t.addSuppressed(second);
            }
            rethrow(t);
        }

        report.status = report.allPassed() ? "PASS" : "FAIL";
        if (!report.allPassed()) {
            List<String> failed = report.assertions.stream()
                .filter(a -> !a.pass()).map(RunReport.Assertion::name).toList();
            report.failureStage = "assertions";
            report.errorMessage = String.join(", ", failed);
        }
        finish(report, cluster);
        // 归档与清理已结束：非 PASS 必须让构建红，否则 CI 会假绿
        if (!("PASS".equals(report.status))) {
            throw new AssertionError("SCH-001 " + report.status + " at " + report.failureStage
                + ": " + report.errorMessage);
        }
    }

    private static final String BROKER_JAR_HINT =
        "集群测试会在当前工作目录下以 -Pcluster-e2e 嵌套打包 fluxion-server-start；"
            + "请从仓库根目录以 mvn test -pl fluxion-test -am -Pcluster-e2e 运行，且要求 mvn 在 PATH 中";

    // ---- 场景步骤 ----

    private void checkPodman() {
        if (!Shell.commandExists("podman")) {
            throw new IllegalStateException("podman not available in PATH");
        }
        if (!Shell.probe(30, "podman", "info")) {
            throw new IllegalStateException("podman machine not running (podman info failed)");
        }
        if (!Shell.commandExists("mvn")) {
            throw new IllegalStateException("mvn not available in PATH");
        }
    }

    /**
     * 嵌套构建 Broker 可执行 jar：同一 reactor 内上游模块 jar 必须先 package 出来，
     * repackage 才能封入完整类路径。必须 clean：IDE 残留的旧 target/classes 会被
     * javac 按时间戳跳过重编译，曾打包出带“Unresolved compilation problems”的错误类。
     * 全量 package 但跳过测试，避免测试递归。
     */
    private void buildBrokerJar() {
        Path repoRoot = Path.of("..").toAbsolutePath().normalize();
        Shell.Result result = Shell.run(repoRoot.toFile(), 900, "mvn",
            "-q", "clean", "-DskipTests", "-Pcluster-e2e",
            "-pl", "fluxion-server/fluxion-server-start", "-am",
            "package");
        if (!result.ok()) {
            throw new IllegalStateException("nested clean package for broker jar failed: "
                + result.stderr());
        }
    }

    private Path locateJar(Path dir, String glob, String hint) {
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("jar directory missing: " + dir.toAbsolutePath() + "; " + hint);
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, glob)) {
            List<Path> matched = new ArrayList<>();
            stream.forEach(matched::add);
            if (matched.size() != 1) {
                throw new IllegalStateException("expected one artifact matching " + glob + " in "
                    + dir.toAbsolutePath() + ", found " + matched.size() + "; " + hint);
            }
            return matched.get(0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void recordGit(RunReport report) {
        Path repoRoot = Path.of("..").toAbsolutePath().normalize();
        Shell.Result commit = Shell.run(repoRoot.toFile(), 30, "git", "rev-parse", "HEAD");
        report.manifestData.put("gitCommit", commit.ok() ? commit.stdout().trim() : "unknown");
        Shell.Result dirty = Shell.run(repoRoot.toFile(), 30, "git", "status", "--porcelain");
        report.manifestData.put("gitDirty", dirty.ok() && !dirty.stdout().isBlank());
    }

    private String createAndEnableTrigger(Cluster cluster, String runId, long t1Ms, String appId) throws Exception {
        Api api = cluster.brokerApi(0);
        String triggerId = api.createTrigger("sch001-" + runId, "cluster e2e SCH-001 fixed rate trigger");
        ObjectNode config = triggerConfig(t1Ms, appId);
        api.draft(triggerId, config);
        api.publish(triggerId, config);
        api.enable(triggerId);
        return triggerId;
    }

    /** 两台 Worker 必须同属一个 App；不同 App 时派发只可能命中其中一个，直接判错。 */
    private String requireWorkersAppId(Cluster cluster) throws Exception {
        var workerPage = cluster.brokerApi(0).workerPage();
        java.util.Set<String> appIds = new java.util.HashSet<>();
        for (var worker : workerPage) {
            appIds.add(worker.path("appId").asText(""));
        }
        if (appIds.size() != 1) {
            throw new IllegalStateException("expected both workers under one app, got: " + appIds);
        }
        return appIds.iterator().next();
    }

    private void snapshotScheduleState(RunReport report, Cluster cluster, String triggerId)
        throws SQLException, IOException {
        report.snapshot("schedule-after-enable",
            cluster.db().query("select * from fluxion_schedule where schedule_id = ?", triggerId));
        report.snapshot("trigger-after-enable",
            cluster.db().query("select * from fluxion_trigger where trigger_id = ?", triggerId));
    }

    /**
     * 等待 Broker 预生成后，从实际 Future 点集中选取最早两个连续点作为目标。
     * 预算覆盖首次扫描拾取（startTime 后 5min 起的任意一次 5min 周期扫描）。
     *
     * @return [t1, t2] 两个连续计划触发点（epoch ms）
     */
    private long[] selectTwoFuturePoints(Cluster cluster, RunReport report, String triggerId)
        throws SQLException, InterruptedException {
        long deadline = System.currentTimeMillis() + PRE_GENERATION_WAIT.toMillis();
        while (true) {
            List<Map<String, Object>> rows = cluster.db().query(
                "select trigger_at from fluxion_execution"
                    + " where trigger_id = ? and is_deleted = 0 and trigger_at > now(3)"
                    + " order by trigger_at asc limit 2", triggerId);
            if (rows.size() >= 2) {
                long t1 = num(rows.get(0).get("trigger_at_ms"));
                long t2 = num(rows.get(1).get("trigger_at_ms"));
                // 选点诊断：选中点必须落在预生成窗口内（now+10min 内），异常即快败
                long now = System.currentTimeMillis();
                System.out.printf("[SCH-001] selected targets t1=%s t2=%s now=%s;%n",
                    iso(t1), iso(t2), iso(now));
                if (t1 <= 0 || t2 <= 0 || t1 <= now || Math.abs(t1 - now) > 13 * 60 * 1000L) {
                    throw new IllegalStateException("implausible selected trigger points: t1="
                        + iso(t1) + " t2=" + iso(t2) + " now=" + iso(now)
                        + " raw0=" + rows.get(0) + " raw1=" + rows.get(1));
                }
                report.record("selected-points-consecutive-interval",
                    TRIGGER_INTERVAL.getSeconds() + "s",
                    ((t2 - t1) / 1000) + "s",
                    Math.abs((t2 - t1) - TRIGGER_INTERVAL.toMillis()) <= TIME_TOLERANCE_MS);
                return new long[]{t1, t2};
            }
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("timeout: no two future executions pre-generated"
                    + " for trigger " + triggerId + " within " + PRE_GENERATION_WAIT.toMinutes() + "min");
            }
            Thread.sleep(10_000);
        }
    }

    /** 等待目标 Execution 到达 SUCCEED；预算 = 计划时刻 + 收敛预算。 */
    private void waitExecutionSucceed(Cluster cluster, String triggerId, long triggerAtMs)
        throws SQLException, InterruptedException {
        long deadline = triggerAtMs + CONVERGE_BUDGET.toMillis();
        while (true) {
            List<Map<String, Object>> rows = targetExecutions(cluster, triggerId, triggerAtMs);
            if (!rows.isEmpty() && "succeed".equalsIgnoreCase(String.valueOf(rows.get(0).get("status")))) {
                return;
            }
            if (System.currentTimeMillis() > deadline) {
                String status = rows.isEmpty() ? "MISSING" : String.valueOf(rows.get(0).get("status"));
                throw new IllegalStateException("timeout: execution for planned trigger point "
                    + iso(triggerAtMs) + " did not reach SUCCEED within budget (status=" + status + ")");
            }
            Thread.sleep(5_000);
        }
    }

    private List<Map<String, Object>> targetExecutions(Cluster cluster, String triggerId, long triggerAtMs)
        throws SQLException {
        return cluster.db().query(
            "select * from fluxion_execution where trigger_id = ? and is_deleted = 0"
                + " and trigger_at >= ? and trigger_at <= ?",
            triggerId,
            LocalDateTime.ofInstant(Instant.ofEpochMilli(triggerAtMs - TIME_TOLERANCE_MS), ZoneOffset.UTC),
            LocalDateTime.ofInstant(Instant.ofEpochMilli(triggerAtMs + TIME_TOLERANCE_MS), ZoneOffset.UTC));
    }

    private ObjectNode triggerConfig(long t1Ms, String appId) {
        ObjectNode config = MAPPER.createObjectNode();
        config.put("type", "schedule");
        ObjectNode scheduleOption = config.putObject("scheduleOption");
        scheduleOption.put("type", "fixed_rate");
        // 统一时间模型：日历语义用显式 IANA 时区（UTC）解释
        scheduleOption.put("startTime",
            LocalDateTime.ofInstant(Instant.ofEpochMilli(t1Ms), ZoneOffset.UTC).toString());
        scheduleOption.put("timeZone", "UTC");
        scheduleOption.put("scheduleInterval", "PT" + TRIGGER_INTERVAL.getSeconds() + "S");
        scheduleOption.put("misfirePolicy", "FIRE_RETRY");
        scheduleOption.put("maxFireAttempts", 1);
        scheduleOption.put("recoveryEnabled", false);

        ObjectNode executeConfig = config.putObject("executeConfig");
        executeConfig.put("type", "executor");
        ObjectNode retry = executeConfig.putObject("retryOption");
        // 关闭业务失败重试，暴露意外失败
        retry.put("retryTimes", 0);
        retry.put("retryInterval", 0);
        retry.put("retryType", "all");
        ObjectNode executor = executeConfig.putObject("executor");
        executor.put("type", "custom");
        executor.put("name", Cluster.EXECUTOR_NAME);
        executor.put("appId", appId);
        executor.put("executeMode", "standalone");
        return config;
    }

    // ---- 断言 ----

    private void assertScenario(RunReport report, Cluster cluster, long t1Ms, long t2Ms,
                                List<Map<String, Object>> executions, List<Map<String, Object>> jobs) {
        Map<String, Object> t1Exec = singleTarget(report, executions, t1Ms, "t1");
        Map<String, Object> t2Exec = singleTarget(report, executions, t2Ms, "t2");
        String publishVersion = String.valueOf(t1Exec.get("executable_version"));

        report.record("t1-execution-status", "SUCCEED", s(t1Exec.get("status")),
            "succeed".equalsIgnoreCase(s(t1Exec.get("status"))));
        report.record("t2-execution-status", "SUCCEED", s(t2Exec.get("status")),
            "succeed".equalsIgnoreCase(s(t2Exec.get("status"))));
        report.record("t1-execution-version", publishVersion, s(t1Exec.get("executable_version")),
            publishVersion.equals(s(t1Exec.get("executable_version"))));
        report.record("t2-execution-version", publishVersion, s(t2Exec.get("executable_version")),
            publishVersion.equals(s(t2Exec.get("executable_version"))));

        Map<String, List<Map<String, Object>>> jobsByExecution = new LinkedHashMap<>();
        for (Map<String, Object> job : jobs) {
            jobsByExecution.computeIfAbsent(s(job.get("execution_id")), k -> new ArrayList<>()).add(job);
        }
        assertTargetJob(report, cluster, "t1", t1Ms, t1Exec,
            jobsByExecution.getOrDefault(s(t1Exec.get("execution_id")), List.of()));
        assertTargetJob(report, cluster, "t2", t2Ms, t2Exec,
            jobsByExecution.getOrDefault(s(t2Exec.get("execution_id")), List.of()));
    }

    private Map<String, Object> singleTarget(RunReport report, List<Map<String, Object>> executions,
                                             long triggerAtMs, String label) {
        List<Map<String, Object>> matched = new ArrayList<>();
        for (Map<String, Object> row : executions) {
            if (isTarget(row, triggerAtMs)) {
                matched.add(row);
            }
        }
        report.record(label + "-identity-unique", "1 execution for planned trigger point "
            + iso(triggerAtMs), String.valueOf(matched.size()), matched.size() == 1);
        if (matched.size() == 1) {
            return matched.get(0);
        }
        Map<String, Object> missing = new LinkedHashMap<>();
        missing.put("execution_id", "MISSING");
        missing.put("status", "MISSING");
        missing.put("executable_version", "MISSING");
        missing.put("trigger_at_ms", 0L);
        missing.put("trigger_at", "MISSING");
        return missing;
    }

    private void assertTargetJob(RunReport report, Cluster cluster, String label, long triggerAtMs,
                                 Map<String, Object> execution, List<Map<String, Object>> targetJobs) {
        report.record(label + "-job-count", "1", String.valueOf(targetJobs.size()), targetJobs.size() == 1);
        if (targetJobs.size() != 1) {
            return;
        }
        Map<String, Object> job = targetJobs.get(0);
        report.record(label + "-job-status", "SUCCEED", s(job.get("status")),
            "succeed".equalsIgnoreCase(s(job.get("status"))));
        report.record(label + "-job-retry-times-zero", "0", s(job.get("retry_times")),
            num(job.get("retry_times")) == 0);
        report.record(label + "-job-trigger-at-inherits-execution",
            s(execution.get("trigger_at_ms")), s(job.get("trigger_at_ms")),
            num(job.get("trigger_at_ms")) > 0
                && Math.abs(num(job.get("trigger_at_ms")) - num(execution.get("trigger_at_ms")))
                <= TIME_TOLERANCE_MS);
        report.record(label + "-job-start-at-not-before-trigger-at", "job.start_at >= job.trigger_at",
            "start_at=" + s(job.get("start_at")) + ", trigger_at=" + s(job.get("trigger_at")),
            job.get("start_at_ms") != null
                && num(job.get("start_at_ms")) >= num(job.get("trigger_at_ms")));
        report.record(label + "-job-execution-relation", "execution_id=" + s(execution.get("execution_id")),
            "execution_id=" + s(job.get("execution_id")),
            s(execution.get("execution_id")).equals(s(job.get("execution_id"))));
        report.record(label + "-job-worker-assigned", "non-empty worker address",
            s(job.get("worker_address")), !s(job.get("worker_address")).isBlank());
        assertBusinessEvents(report, cluster, job);
    }

    private void assertBusinessEvents(RunReport report, Cluster cluster, Map<String, Object> job) {
        String jobId = s(job.get("job_id"));
        List<Map<String, Object>> jobEvents = cluster.events().eventsOfJob(jobId);
        long starts = jobEvents.stream().filter(e -> "BUSINESS_START".equals(e.get("type"))).count();
        long finishes = jobEvents.stream().filter(e -> "BUSINESS_FINISH".equals(e.get("type"))).count();
        report.record("events-" + jobId + "-business-start", "1", String.valueOf(starts), starts == 1);
        report.record("events-" + jobId + "-business-finish", "1", String.valueOf(finishes), finishes == 1);
        Map<String, Object> start = first(jobEvents, "BUSINESS_START");
        Map<String, Object> finish = first(jobEvents, "BUSINESS_FINISH");
        if (start == null || finish == null) {
            return;
        }
        int eventWorkerPort = cluster.workerPorts().getOrDefault(s(start.get("workerTag")), -1);
        String expectedWorker = "http://127.0.0.1:" + eventWorkerPort;
        report.record("events-" + jobId + "-worker-matches-dispatch",
            expectedWorker, s(job.get("worker_address")),
            expectedWorker.equals(s(job.get("worker_address"))));
        report.record("events-" + jobId + "-executor-identity",
            Cluster.EXECUTOR_NAME, s(start.get("executor")),
            Cluster.EXECUTOR_NAME.equals(s(start.get("executor"))));
        report.record("events-" + jobId + "-business-output", "fluxion-cluster-e2e-ok",
            s(finish.get("output")), "fluxion-cluster-e2e-ok".equals(s(finish.get("output"))));
        report.record("events-" + jobId + "-business-start-not-before-trigger-at",
            "event.at >= planned trigger_at (" + iso(num(job.get("trigger_at_ms"))) + ")",
            "event.at=" + iso(num(start.get("at"))),
            num(start.get("at")) >= num(job.get("trigger_at_ms")));
        report.record("events-" + jobId + "-finish-after-start", "startEvent.at <= finishEvent.at",
            "start=" + iso(num(start.get("at"))) + ", finish=" + iso(num(finish.get("at"))),
            num(start.get("at")) <= num(finish.get("at")));
    }

    // ---- 收尾 ----

    /**
     * 无论成败：先归档产物与节点证据，再清理资源；归档/清理失败升级为运行级 ERROR
     * 且不掩盖原始场景失败；results.json 最后落盘，包含清理阶段的错误；
     * results 写入失败则抛出（无法自证时必须让外部看到失败）。
     */
    private void finish(RunReport report, Cluster cluster) {
        List<Throwable> runLevelErrors = new ArrayList<>();
        List<Map<String, Object>> nodes = List.of();
        try {
            nodes = cluster == null ? List.of() : cluster.nodeEvidence();
            report.writeEvents(cluster == null ? List.of() : cluster.events().events());
            report.writeAssertions();
            report.writeManifest(Map.of("nodes", nodes));
        } catch (Throwable t) {
            runLevelErrors.add(t);
        }
        if (cluster != null) {
            try {
                cluster.close();
            } catch (Throwable t) {
                runLevelErrors.add(t);
            }
        }
        if (!runLevelErrors.isEmpty()) {
            String detail = "report/cleanup: " + runLevelErrors.get(0).getMessage();
            report.errorMessage = report.errorMessage.isEmpty() ? detail : report.errorMessage + "; " + detail;
            // 已有场景失败时只追加错误信息，保留其中 failureStage（不掩盖）
            if ("PASS".equals(report.status)) {
                report.status = "ERROR";
                report.failureStage = "report";
            }
        }
        try {
            report.writeResults(System.currentTimeMillis(), nodes);
        } catch (Throwable t) {
            for (Throwable suppressed : runLevelErrors) {
                t.addSuppressed(suppressed);
            }
            throw new RuntimeException("cannot write results.json", t);
        }
    }

    private static void rethrow(Throwable t) {
        if (t instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (t instanceof Error error) {
            throw error;
        }
        throw new RuntimeException(t);
    }

    // ---- 小工具 ----

    private static long num(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static String s(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private static Map<String, Object> first(List<Map<String, Object>> events, String type) {
        for (Map<String, Object> event : events) {
            if (type.equals(event.get("type"))) {
                return event;
            }
        }
        return null;
    }

    private static boolean isTarget(Map<String, Object> row, long triggerAtMs) {
        return row.get("trigger_at_ms") != null
            && Math.abs(num(row.get("trigger_at_ms")) - triggerAtMs) <= TIME_TOLERANCE_MS;
    }

    private static long alignedSecond(Instant instant) {
        return instant.toEpochMilli() / 1000 * 1000;
    }

    private static String iso(long epochMs) {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMs));
    }
}