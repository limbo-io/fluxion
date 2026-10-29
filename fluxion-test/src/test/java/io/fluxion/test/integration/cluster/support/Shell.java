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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 外部命令执行（podman/git 等），带超时并捕获输出。
 */
public final class Shell {

    private static final int STREAM_TIMEOUT_SECONDS = 30;

    /** 命令执行结果。 */
    public record Result(int exitCode, String stdout, String stderr) {
        public boolean ok() {
            return exitCode == 0;
        }
    }

    private Shell() {
    }

    public static boolean commandExists(String command) {
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                if (new File(dir, command).canExecute()) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Result run(long timeoutSeconds, String... command) {
        return run(new File(System.getProperty("user.dir")), timeoutSeconds, command);
    }

    public static Result run(File workDir, long timeoutSeconds, String... command) {
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(false).directory(workDir).start();
            var stdoutReader = new StreamGobbler(p.getInputStream());
            var stderrReader = new StreamGobbler(p.getErrorStream());
            stdoutReader.start();
            stderrReader.start();
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return new Result(-1, stdoutReader.text(), stderrReader.text() + " [timeout after " + timeoutSeconds + "s]");
            }
            stdoutReader.await(STREAM_TIMEOUT_SECONDS);
            stderrReader.await(STREAM_TIMEOUT_SECONDS);
            return new Result(p.exitValue(), stdoutReader.text(), stderrReader.text());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new Result(-1, "", String.valueOf(e));
        }
    }

    /** 静默探测命令是否执行成功。 */
    public static boolean probe(long timeoutSeconds, String... command) {
        return run(timeoutSeconds, command).ok();
    }

    private static final class StreamGobbler extends Thread {
        private final java.io.InputStream in;
        private final StringBuilder sb = new StringBuilder();

        StreamGobbler(java.io.InputStream in) {
            this.in = in;
            setDaemon(true);
        }

        @Override
        public void run() {
            try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            } catch (IOException ignore) {
                // 进程被杀时流关闭
            }
        }

        void await(int seconds) throws InterruptedException {
            join(seconds * 1000L);
        }

        String text() {
            return sb.toString();
        }
    }
}