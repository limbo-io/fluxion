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
import java.net.ServerSocket;

/**
 * 集群端口分配：探测空闲端口后释放，存在极小的复用竞态窗口，以“端口被占用时启动失败”兜底。
 */
public final class FreePort {

    private FreePort() {
    }

    public static int next() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("no free port available", e);
        }
    }
}