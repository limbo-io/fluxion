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

package io.fluxion.test.cluster.support;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行产物与报告：<run-id>/{manifest.json,results.json,report.html,junit.xml} 与
 * cases/&lt;case&gt;/&lt;variant&gt;/{events.jsonl,assertions.json,snapshots/}。
 * HTML/JSON/JUnit 使用同一份结果数据；未观测到的数据不填造。
 */
public class RunReport {

    /** 用例状态：与实现与验收约定的六状态一致。 */
    public enum Status { PASS, FAIL, ERROR, TIMEOUT }

    public record Assertion(String name, String expected, String actual, boolean pass, String note) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public final String runId;
    public final String caseId;
    public final String variant;
    public final Path runRoot;
    public final Path caseDir;
    public final long startedAtMs = System.currentTimeMillis();
    public final List<Assertion> assertions = new ArrayList<>();
    public final Map<String, Long> stageMarksMs = new LinkedHashMap<>();
    public String status = "PASS";
    public String failureStage = "";
    public String errorMessage = "";
    /** 运行清单补充：资源身份、节点、耗时等实际数据。 */
    public final Map<String, Object> manifestData = new LinkedHashMap<>();

    public RunReport(String runId, String caseId, String variant, Path baseDir) throws IOException {
        this.runId = runId;
        this.caseId = caseId;
        this.variant = variant;
        this.runRoot = baseDir.resolve(runId);
        this.caseDir = runRoot.resolve("cases").resolve(caseId).resolve(variant);
        Files.createDirectories(caseDir.resolve("snapshots"));
    }

    // ---- 证据记录 ----

    public void record(String name, Object expected, Object actual, boolean pass) {
        assertions.add(new Assertion(name, str(expected), str(actual), pass, ""));
    }

    public void mark(String stage) {
        stageMarksMs.put(stage, System.currentTimeMillis());
    }

    public boolean allPassed() {
        return assertions.stream().allMatch(Assertion::pass);
    }

    /** 快照：数据库只读行 / 事件等，写入 cases/<case>/<variant>/snapshots/<name>.json。 */
    public void snapshot(String name, Object data) throws IOException {
        Files.writeString(caseDir.resolve("snapshots").resolve(name + ".json"),
            MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(data));
    }

    /** 业务事件证据：events.jsonl。 */
    public void writeEvents(List<Map<String, Object>> events) throws IOException {
        StringBuilder jsonl = new StringBuilder();
        for (Map<String, Object> event : events) {
            jsonl.append(MAPPER.writeValueAsString(event)).append('\n');
        }
        Files.writeString(caseDir.resolve("events.jsonl"), jsonl.toString());
    }

    /** 每项断言、预期、实际值、结论及关联证据。 */
    public void writeAssertions() throws IOException {
        Files.writeString(caseDir.resolve("assertions.json"),
            MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                "caseId", caseId,
                "variant", variant,
                "assertions", assertions)));
    }

    // ---- 运行级产物 ----

    public void writeManifest(Map<String, Object> resources) throws IOException {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("runId", runId);
        manifest.put("startedAt", iso(startedAtMs));
        manifest.put("caseSelection", List.of(caseId + "/" + variant));
        manifest.put("gitCommit", manifestData.getOrDefault("gitCommit", ""));
        manifest.put("gitDirty", manifestData.getOrDefault("gitDirty", false));
        manifest.put("javaVersion", System.getProperty("java.version", ""));
        manifest.put("timeParamsMode", "default");
        manifest.put("resources", resources);
        Files.writeString(runRoot.resolve("manifest.json"),
            MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(manifest));
    }

    /** results.json：与 HTML/JUnit 同源的数据。 */
    public void writeResults(long endedAtMs, List<Map<String, Object>> nodeEvidence) throws IOException {
        Map<String, Object> results = new LinkedHashMap<>();
        results.put("runId", runId);
        results.put("status", status);
        results.put("failureStage", failureStage);
        results.put("errorMessage", errorMessage);
        results.put("startedAt", iso(startedAtMs));
        results.put("endedAt", iso(endedAtMs));
        results.put("durationMs", endedAtMs - startedAtMs);
        results.put("stageMarks", new LinkedHashMap<>(stageMarksMs));
        Map<String, Object> caseResult = new LinkedHashMap<>();
        caseResult.put("caseId", caseId);
        caseResult.put("variant", variant);
        caseResult.put("status", status);
        caseResult.put("failureStage", failureStage);
        caseResult.put("assertionCount", assertions.size());
        caseResult.put("assertionFailures", assertions.stream().filter(a -> !a.pass()).count());
        caseResult.put("assertions", assertions);
        caseResult.put("artifacts", Map.of(
            "events", relative(caseDir.resolve("events.jsonl")),
            "assertions", relative(caseDir.resolve("assertions.json")),
            "snapshots", relative(caseDir.resolve("snapshots"))));
        caseResult.put("nodes", nodeEvidence);
        results.put("cases", List.of(caseResult));
        Files.writeString(runRoot.resolve("results.json"),
            MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(results));
        Files.writeString(runRoot.resolve("report.html"), html(results));
        Files.writeString(runRoot.resolve("junit.xml"), junit(results));
    }

    private String relative(Path path) {
        return runRoot.relativize(path).toString().replace('\\', '/');
    }

    // ---- 渲染（同一数据，不同形态） ----

    private String html(Map<String, Object> results) {
        String failStyle = "PASS".equals(status) ? "" : " style=\"color:#b00;font-weight:bold\"";
        StringBuilder rows = new StringBuilder();
        for (Assertion a : assertions) {
            rows.append("<tr><td>").append(esc(a.name()))
                .append("</td><td>").append(a.pass() ? "PASS" : "FAIL")
                .append("</td><td><code>").append(esc(a.expected()))
                .append("</code></td><td><code>").append(esc(a.actual()))
                .append("</td></tr>\n");
        }
        return """
            <!DOCTYPE html>
            <html lang="zh">
            <head>
              <meta charset="utf-8">
              <title>fluxion cluster e2e %s</title>
              <style>
                body { font-family: -apple-system, sans-serif; margin: 2rem; color: #222; }
                table { border-collapse: collapse; margin: 1rem 0; }
                th, td { border: 1px solid #ccc; padding: .4rem .7rem; text-align: left;
                         vertical-align: top; font-size: .9rem; }
                code { font-size: .85em; }
                h2 { margin-top: 2rem; }
              </style>
            </head>
            <body>
              <h1>Cluster E2E — %s</h1>
              <p>runId: <code>%s</code>　status: <span%s>%s</span>　duration: %ds</p>
              <p>failureStage: <code>%s</code></p>
              <h2>Assertions (%d)</h2>
              <table>
                <tr><th>assertion</th><th>result</th><th>expected</th><th>actual</th></tr>
                %s
              </table>
              <h2>Artifacts</h2>
              <p><a href="results.json">results.json</a> ·
                 <a href="manifest.json">manifest.json</a> ·
                 <a href="%s/events.jsonl">events.jsonl</a> ·
                 <a href="%s/assertions.json">assertions.json</a> ·
                 <a href="nodes/">node logs</a></p>
            </body>
            </html>
            """.formatted(
            esc(runId), esc(caseId + "/" + variant), esc(runId), failStyle, status,
            (long) results.get("durationMs") / 1000, esc(failureStage + (errorMessage.isEmpty() ? ""
                : " — " + errorMessage)), assertions.size(), rows,
            relative(caseDir), relative(caseDir));
    }

    private String junit(Map<String, Object> results) {
        boolean failure = "FAIL".equals(status);
        boolean error = "ERROR".equals(status) || "TIMEOUT".equals(status);
        String body = "";
        if (failure || error) {
            String message = esc(failureStage + ": " + errorMessage);
            if (error) {
                body = "<error message=\"" + message + "\" type=\"" + status + "\"/>";
            } else {
                body = "<failure message=\"" + message + "\" type=\"assertion\"/>";
            }
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="cluster-e2e" tests="1" failures="%d" errors="%d" skipped="0" time="%.3f">
              <testcase classname="io.fluxion.test.cluster.Sch001Test" name="sch001_fixed_rate_trigger_twice">%s</testcase>
            </testsuite>
            """.formatted(failure ? 1 : 0, error ? 1 : 0,
            ((long) results.get("durationMs")) / 1000.0, body);
    }

    private static String str(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private static String iso(long epochMs) {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMs));
    }

    private static String esc(String value) {
        return value == null ? "" : value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}