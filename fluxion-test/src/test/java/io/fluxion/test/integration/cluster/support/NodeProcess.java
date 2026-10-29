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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 集群节点 Java 进程。stdout/stderr 全量落盘到本运行的节点日志目录，
 * 清理只作用于本场景启动的进程句柄。
 */
public class NodeProcess {

    private final String name;
    private final Process process;
    private final File logFile;

    public NodeProcess(String name, File logFile, List<String> command) throws IOException {
        this.name = name;
        this.logFile = logFile;
        ProcessBuilder pb = new ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(processOutput(logFile));
        this.process = pb.start();
    }

    private static ProcessBuilder.Redirect processOutput(File logFile) throws IOException {
        File parent = logFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("cannot create log dir: " + parent);
        }
        return ProcessBuilder.Redirect.to(logFile);
    }

    public String name() {
        return name;
    }

    public File logFile() {
        return logFile;
    }

    public long pid() {
        return process.pid();
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    public String exitStatus() {
        if (process.isAlive()) {
            return "ALIVE";
        }
        return String.valueOf(process.exitValue());
    }

    /**
     * 强制退出（kill 信号），等待有限时间；不等待业务清理钩子。
     */
    public void kill() {
        process.destroyForcibly();
        try {
            process.waitFor(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}