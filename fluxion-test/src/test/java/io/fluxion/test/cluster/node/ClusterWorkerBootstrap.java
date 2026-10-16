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

package io.fluxion.test.cluster.node;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * 集群 E2E Worker 节点启动入口。
 *
 * <p>由 fluxion-test 打包为 cluster-e2e 测试 jar 后，以独立进程启动：
 * {@code java -D... -jar fluxion-test-*-cluster-e2e.jar --fluxion.worker.port=...}。
 * 测试执行器与事件上报属性全部通过进程参数注入，不依赖业务代码改动。</p>
 */
public final class ClusterWorkerBootstrap {

    private ClusterWorkerBootstrap() {
    }

    public static void main(String[] args) {
        new SpringApplicationBuilder()
            .web(WebApplicationType.NONE)
            .sources(ClusterWorkerApp.class)
            .build()
            .run(args);
    }
}