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

import io.fluxion.test.integration.cluster.support.Cluster;
import io.fluxion.test.integration.cluster.support.RunReport;
import io.fluxion.test.integration.cluster.support.Shell;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 本地集群场景共用的构建、报告与资源回收。 */
abstract class ClusterTestSupport {

    static final String BROKER_JAR_HINT =
        "集群测试会在当前工作目录下以 -Pcluster-e2e 嵌套打包 fluxion-server-start；"
            + "请从仓库根目录以 mvn test -pl fluxion-test -am -Pcluster-e2e 运行，且要求 mvn 在 PATH 中";

    final void checkPodman() {
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
    final void buildBrokerJar() {
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

    final Path locateJar(Path dir, String glob, String hint) {
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

    final void recordGit(RunReport report) {
        Path repoRoot = Path.of("..").toAbsolutePath().normalize();
        Shell.Result commit = Shell.run(repoRoot.toFile(), 30, "git", "rev-parse", "HEAD");
        report.manifestData.put("gitCommit", commit.ok() ? commit.stdout().trim() : "unknown");
        Shell.Result dirty = Shell.run(repoRoot.toFile(), 30, "git", "status", "--porcelain");
        report.manifestData.put("gitDirty", dirty.ok() && !dirty.stdout().isBlank());
    }

    /**
     * 无论成败：先归档产物与节点证据，再清理资源；归档/清理失败升级为运行级 ERROR
     * 且不掩盖原始场景失败；results.json 最后落盘，包含清理阶段的错误；
     * results 写入失败则抛出（无法自证时必须让外部看到失败）。
     */
    final void finish(RunReport report, Cluster cluster) {
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
            // 权威 results 由 writeResults 自证；此处抛出均带原因与 suppressed，不再重新包装失真
            for (Throwable suppressed : runLevelErrors) {
                t.addSuppressed(suppressed);
            }
            rethrow(t);
        }
    }

    static void rethrow(Throwable t) {
        if (t instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (t instanceof Error error) {
            throw error;
        }
        throw new RuntimeException(t);
    }

}
