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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 本运行专用 Podman MySQL 容器。镜像版本固定（不使用 latest），
 * 容器、库、端口均按 runId 命名/分配，结束后显式删除。
 */
public class MySqlContainer {

    /** 与 mysql-integration 口径一致的固定镜像。 */
    public static final String IMAGE = "docker.io/library/mysql:8.4";

    private final String name;
    private final int port;
    private final String database = "fluxion";
    private final String rootPassword;

    /** 容器 id（podman run 输出）。 */
    private String containerId = "";

    public MySqlContainer(String name, int port, String rootPassword) {
        this.name = name;
        this.port = port;
        this.rootPassword = rootPassword;
    }

    /**
     * 检查并按需拉取镜像，启动容器并等待 mysqld 就绪。
     */
    public void start(Path nodesLogDir) throws IOException {
        if (!Shell.probe(60, "podman", "image", "exists", IMAGE)) {
            Shell.Result pull = Shell.run(600, "podman", "pull", IMAGE);
            if (!pull.ok()) {
                throw new IOException("podman pull " + IMAGE + " failed: " + pull.stderr());
            }
        }
        Shell.Result run = Shell.run(60, "podman", "run", "-d", "--name", name,
            "-p", "127.0.0.1:" + port + ":3306",
            "-e", "MYSQL_ROOT_PASSWORD=" + rootPassword,
            "-e", "MYSQL_DATABASE=" + database,
            IMAGE);
        if (!run.ok()) {
            throw new IOException("podman run failed: " + run.stderr());
        }
        containerId = run.stdout().trim();
        Files.writeString(nodesLogDir.resolve(name + ".log"),
            "container id: " + containerId + "\n");
        Await.until("mysql container ready", Duration.ofMinutes(5), Duration.ofSeconds(2), () -> {
            Shell.Result ping = Shell.run(30, "podman", "exec", name,
                "mysqladmin", "--protocol=tcp", "-h127.0.0.1", "-uroot",
                "-p" + rootPassword, "ping");
            return ping.ok() && ping.stdout().contains("mysqld is alive");
        });
    }

    /** 生产同口径的 JDBC URL（useAffectedRows、UTC 会话由 Broker 侧连接参数保证）。 */
    public String jdbcUrl() {
        return "jdbc:mysql://127.0.0.1:" + port + "/" + database
            + "?useUnicode=true&characterEncoding=UTF-8&allowMultiQueries=true"
            + "&serverTimezone=UTC&useAffectedRows=true&allowPublicKeyRetrieval=true";
    }

    public String username() {
        return "root";
    }

    public String password() {
        return rootPassword;
    }

    public int port() {
        return port;
    }

    public boolean jdbcConnectable() {
        try (Connection ignored = DriverManager.getConnection(jdbcUrl(), username(), password())) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 删除本场景容器；对容器不存在的情形幂等。 */
    public void remove() {
        List<String> cleanupErrors = new ArrayList<>();
        if (!name.isEmpty()) {
            Shell.Result rm = Shell.run(60, "podman", "rm", "-f", name);
            if (!rm.ok() && !rm.stderr().contains("no such container")) {
                cleanupErrors.add(rm.stderr());
            }
        }
        if (!cleanupErrors.isEmpty()) {
            throw new IllegalStateException("mysql cleanup failed: " + String.join("; ", cleanupErrors));
        }
    }
}