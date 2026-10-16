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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 隔离环境生命周期：本运行专用 MySQL 容器 + 2 Broker 独立进程 + 2 Worker 独立进程。
 *
 * <p>启动顺序与就绪条件遵循实现与验收约定：先 Broker 就绪（API/注册/Bucket 归属），
 * 后 Worker（注册/心跳/执行器）。清理只作用于本场景启动的进程与容器。</p>
 */
public class Cluster implements AutoCloseable {

    public static final String EXECUTOR_NAME = "io.fluxion.test.cluster.node.TestExecutor";

    private final String runId;
    private final Path runDir;
    private final MySqlContainer mysql;
    private EventReceiver events;
    private Db db;
    private final List<NodeProcess> nodes = new ArrayList<>();
    private final int[] brokerApiPorts = new int[2];
    private final int[] brokerCommPorts = new int[2];
    private final String[] workerTags = {"worker-1", "worker-2"};
    private final Map<String, Integer> workerPorts = new LinkedHashMap<>();
    private final List<Api> brokerApis = new ArrayList<>();
    private final Map<String, Long> stageMarksMs = new LinkedHashMap<>();
    /** broker 进程按索引登记，启动失败诊断时读取退出状态与日志尾部。 */
    private final Map<Integer, NodeProcess> brokerNodes = new LinkedHashMap<>();

    public Cluster(String runId, Path runDir) throws IOException {
        this.runId = runId;
        this.runDir = runDir;
        this.mysql = new MySqlContainer(runId + "-mysql", FreePort.next(), "fluxion-e2e");
        try {
            Files.createDirectories(nodesDir());
            mysql.start(nodesDir());
            this.events = new EventReceiver(runId);
            this.db = new Db(mysql.jdbcUrl(), mysql.username(), mysql.password());
        } catch (Exception e) {
            // 构造器抛出后调用方拿不到实例，只能在此回收已创建资源；
            // 否则就绪超时等初始化失败会泄漏整个 MySQL 容器
            if (events != null) {
                try {
                    events.close();
                } catch (Exception ignore) {
                    // 回收失败不可掩盖原始初始化错误
                }
            }
            try {
                mysql.remove();
            } catch (Exception ignore) {
                // 已在丢弃初始化路径，清理自身失败不可掩盖原始初始化错误
            }
            if (e instanceof SQLException sqlException) {
                throw new IOException("mysql db connect failed: " + sqlException.getMessage(), sqlException);
            }
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("cluster init failed: " + e.getMessage(), e);
        }
        mark("mysql-ready");
    }

    // ---- 节点启动与就绪 ----

    public void start(Path brokerJar) throws IOException, InterruptedException, SQLException {
        // 顺序启动：IDCommandService.registerIds 冷启动时 findById-then-insert 存在并发竞态
        //（并发冷启动会撞 uk_id 唯一键），首个 Broker 完全就绪后再启第二个
        startBroker(0, brokerJar);
        waitBrokerApiReady(0);
        startBroker(1, brokerJar);
        waitBrokerApiReady(1);
        waitBrokersRegistered();
        // 顺序启动：两 Worker 同时注册会在 WorkerSaveCmd 事务内并发碰撞锁
        //（实测 MySQL Deadlock，后注册者收错误响应且 worker 退出口 NPE）；一次注册一个
        for (int i = 0; i < workerTags.length; i++) {
            String tag = workerTags[i];
            startWorker(tag);
            waitWorkerOnline(tag);
        }
        waitWorkersReady();
    }

    /** 等单个 Worker 在两个 Broker API 视图中均在线且已注册执行器。 */
    private void waitWorkerOnline(String tag) throws InterruptedException {
        int port = workerPorts.get(tag);
        long deadline = System.currentTimeMillis() + Duration.ofMinutes(2).toMillis();
        while (true) {
            if (brokerApis.stream().allMatch(api -> workerReady(api, port))) {
                return;
            }
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("worker " + tag + " not online in time"
                    + "; broker page dump:\n" + workerPageDump());
            }
            Thread.sleep(2_000);
        }
    }

    private void waitBrokersRegistered() throws SQLException, InterruptedException {
        Await.until("broker registered in db", Duration.ofMinutes(2), Duration.ofSeconds(2), () -> {
            try {
                return db.query("select count(*) as total from fluxion_broker where is_deleted = 0").get(0)
                    .get("total").equals(2L);
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
        // 实测语义：BucketRebalanceCmd 仅重选失效归属，存活 Broker 的 bucket 不转移；
        // 先启 Broker 独得全部 64 个 bucket 属预期行为。
        // 就绪判据：64 个 bucket 存在且全部归属于存活（30s 内心跳）Broker。
        Await.until("bucket ownership settled on alive brokers", Duration.ofMinutes(2),
            Duration.ofSeconds(2), () -> {
                try {
                    List<Map<String, Object>> rows = db.query(
                        "select count(*) as total, "
                            + "sum(case when b.broker_id in "
                            + "  (select md5(concat(host, ':', port)) from fluxion_broker"
                            + "    where is_deleted = 0"
                            + "      and last_heartbeat_at >= (now(3) - interval 30 second)) "
                            + "then 1 else 0 end) as owned_by_alive "
                            + "from fluxion_bucket b where b.is_deleted = 0");
                    Map<String, Object> row = rows.get(0);
                    return Long.valueOf(64).equals(row.get("total"))
                        && row.get("owned_by_alive") != null
                        && Long.valueOf(64).equals(((Number) row.get("owned_by_alive")).longValue());
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            });
        mark("brokers-ready");
    }

    private void startBroker(int index, Path jarFile) throws IOException {
        int apiPort = FreePort.next();
        int commPort = FreePort.next();
        String name = "broker-" + (index + 1);
        brokerApiPorts[index] = apiPort;
        brokerCommPorts[index] = commPort;
        List<String> command = new ArrayList<>(List.of(
            javaBin(),
            "-Dfile.encoding=UTF-8",
            "-jar", jarFile.toString(),
            "--server.port=" + apiPort,
            // dev profile 会用 DriverSpy/h2 覆盖驱动，集群必须显式钉住生产驱动与 UTC 会话初始化
            "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
            "--spring.datasource.url=" + mysql.jdbcUrl(),
            "--spring.datasource.username=" + mysql.username(),
            "--spring.datasource.password=" + mysql.password(),
            "--spring.datasource.hikari.connection-init-sql=set names utf8mb4; set time_zone='+00:00'",
            "--fluxion.broker.host=127.0.0.1",
            "--fluxion.broker.port=" + commPort
        ));
        nodes.add(new NodeProcess(name, nodesDir().resolve(name + ".log").toFile(), command));
        brokerNodes.put(index, nodes.get(nodes.size() - 1));
        brokerApis.add(new Api(apiPort));
    }

    private void waitBrokerApiReady(int index) throws InterruptedException, IOException {
        Api api = brokerApis.get(index);
        NodeProcess node = brokerNodes.get(index);
        // broker 进程意外退出时立即携带退出码与日志尾部报错，不空转满预算
        Await.until("broker api ready (" + (index + 1) + ")", Duration.ofMinutes(3),
            Duration.ofSeconds(2), () -> {
                if (!node.isAlive()) {
                    throw new IllegalStateException("broker " + node.name()
                        + " exited early (exit=" + node.exitStatus() + ")\n--- log tail ---\n"
                        + logTail(node.logFile().toPath()));
                }
                try {
                    api.observationOverview();
                    return true;
                } catch (Exception e) {
                    return false;
                }
            });
        mark("broker-api-ready-" + (index + 1));
    }

    /** 节点日志尾部（失败诊断用，最多 40 行）。 */
    private static String logTail(Path logFile) {
        try {
            List<String> lines = Files.readAllLines(logFile);
            return String.join("\n", lines.subList(Math.max(0, lines.size() - 40), lines.size()));
        } catch (IOException e) {
            return "(log unreadable: " + e.getMessage() + ")";
        }
    }

    private void waitWorkersReady() throws InterruptedException, IOException {
        long deadline = System.currentTimeMillis() + Duration.ofMinutes(4).toMillis();
        while (true) {
            boolean ready = brokerApis.stream().allMatch(this::workersReady);
            if (ready) {
                break;
            }
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("workers online with executor registered (both brokers)"
                    + " timeout; broker page dump:\n" + workerPageDump());
            }
            Thread.sleep(2_000);
        }
        mark("workers-ready");
    }

    /** 失败诊断：转储两个 Broker API 的 worker page 实际返回。 */
    private String workerPageDump() {
        StringBuilder dump = new StringBuilder();
        for (int i = 0; i < brokerApis.size(); i++) {
            try {
                dump.append("--- broker-").append(i + 1).append(" page ---\n")
                    .append(brokerApis.get(i).workerPage().toPrettyString()).append('\n');
            } catch (Exception e) {
                dump.append("--- broker-").append(i + 1).append(" page failed: ").append(e).append('\n');
            }
        }
        return dump.toString();
    }

    private boolean workersReady(Api api) {
        try {
            var data = api.workerPage();
            if (!data.isArray() || data.size() < 2) {
                return false;
            }
            int ready = 0;
            for (var worker : data) {
                if (workerReady(api, worker.path("port").asInt(-1))) {
                    ready++;
                }
            }
            return ready >= 2;
        } catch (Exception e) {
            return false;
        }
    }

    /** 指定端口对应的 Worker 是否在线且已注册测试执行器。 */
    private boolean workerReady(Api api, int port) {
        try {
            var data = api.workerPage();
            if (!data.isArray()) {
                return false;
            }
            for (var worker : data) {
                if (worker.path("port").asInt(-1) != port) {
                    continue;
                }
                boolean online = worker.path("status").asText("").equalsIgnoreCase("online");
                boolean hasExecutor = false;
                for (var executor : worker.path("executors")) {
                    if (EXECUTOR_NAME.equals(executor.path("name").asText())) {
                        hasExecutor = true;
                        break;
                    }
                }
                return online && hasExecutor;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private void startWorker(String tag) throws IOException {
        int port = FreePort.next();
        workerPorts.put(tag, port);
        Path workDir = runDir.resolve("nodes").resolve(tag);
        Files.createDirectories(workDir);
        List<String> command = new ArrayList<>(List.of(
            javaBin(),
            "-Dfile.encoding=UTF-8",
            "-De2e.event.url=" + events.url(),
            "-De2e.run.id=" + runId,
            "-De2e.worker.tag=" + tag,
            "-cp", testClasspath(),
            "io.fluxion.test.cluster.node.ClusterWorkerBootstrap",
            "--spring.application.name=fluxion-cluster-e2e-worker",
            "--fluxion.worker.host=127.0.0.1",
            "--fluxion.worker.port=" + port,
            "--fluxion.worker.brokers[0]=http://127.0.0.1:" + brokerCommPorts[0],
            "--fluxion.worker.brokers[1]=http://127.0.0.1:" + brokerCommPorts[1],
            "--fluxion.worker.datasource.url=jdbc:h2:file:" + workDir.toAbsolutePath()
                + "/task;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;MODE=mysql"
        ));
        nodes.add(new NodeProcess(tag + "-process", nodesDir().resolve(tag + ".log").toFile(), command));
    }

    /**
     * Worker 不打包：直接复用 surefire 提供的测试类路径（test-classes + 本次 reactor
     * 构建的模块 classes/依赖 jar），保证节点运行当前代码。
     */
    private static String testClasspath() {
        String cp = System.getProperty("surefire.test.class.path", "");
        if (cp.isBlank()) {
            throw new IllegalStateException("surefire.test.class.path not set; workers must be"
                + " launched from a surefire JVM (mvn test -Pcluster-e2e)");
        }
        return cp;
    }

    // ---- 观测入口 ----

    public Db db() {
        return db;
    }

    /** Broker API 客户端：0 与 1 分别对应 broker-1/broker-2。 */
    public Api brokerApi(int index) {
        return brokerApis.get(index);
    }

    public EventReceiver events() {
        return events;
    }

    /** Worker 事件携带 workerTag；job.worker_address 应等于 tag → 127.0.0.1:port。 */
    public Map<String, Integer> workerPorts() {
        return workerPorts;
    }

    public Map<String, Long> stageMarksMs() {
        return stageMarksMs;
    }

    /** 清理前收集节点证据：名称/PID/日志相对路径/存活状态/退出码，均为实际数据。 */
    public List<Map<String, Object>> nodeEvidence() {
        List<Map<String, Object>> evidence = new ArrayList<>();
        for (NodeProcess node : nodes) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", node.name());
            item.put("pid", node.pid());
            item.put("log", runDir.relativize(node.logFile().toPath()).toString());
            item.put("aliveAtEnd", node.isAlive());
            item.put("exit", node.exitStatus());
            evidence.add(item);
        }
        return evidence;
    }

    private Path nodesDir() {
        return runDir.resolve("nodes");
    }

    private void mark(String stage) {
        stageMarksMs.put(stage, System.currentTimeMillis());
    }

    private static String javaBin() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    @Override
    public void close() {
        List<String> cleanupErrors = new ArrayList<>();
        try {
            events.close();
        } catch (Exception e) {
            cleanupErrors.add("event receiver: " + e);
        }
        for (NodeProcess node : nodes) {
            if (node.isAlive()) {
                node.kill();
                if (node.isAlive()) {
                    cleanupErrors.add("process still alive: " + node.name());
                }
            }
        }
        try {
            db.close();
        } catch (Exception ignore) {
            // 只读连接
        }
        try {
            mysql.remove();
        } catch (Exception e) {
            cleanupErrors.add("mysql remove: " + e);
        }
        if (!cleanupErrors.isEmpty()) {
            throw new IllegalStateException("cleanup failed: " + String.join("; ", cleanupErrors));
        }
    }
}