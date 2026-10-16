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

import io.fluxion.worker.springboot.starter.configuration.FluxionWorkerAutoConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * 集群 E2E Worker 节点启动入口。
 *
 * <p>以独立进程启动：{@code java -D... -cp <test-classpath>
 * io.fluxion.test.cluster.node.ClusterWorkerBootstrap --fluxion.worker.port=...}。
 * 测试执行器与事件上报属性全部通过进程参数注入，不依赖业务代码改动。</p>
 *
 * <p>装配刻意显式：{@code sources} 直接指定 Worker 自动配置与节点上下文，
 * 全程无 @EnableAutoConfiguration——本类若被集成测试的
 * {@code @ComponentScan("io.fluxion")} 捕获（ClusterWorkerApp 曾因注解级
 * exclude 把 DataSource 排除项泄漏进 H2 集成上下文），也不携带任何
 * 自动配置副作用；同路径类间也不会重复装配 worker starter。</p>
 */
public final class ClusterWorkerBootstrap {

    private ClusterWorkerBootstrap() {
    }

    public static void main(String[] args) {
        new SpringApplicationBuilder()
            .web(WebApplicationType.NONE)
            .sources(FluxionWorkerAutoConfiguration.class, ClusterWorkerApp.class)
            .build()
            .run(args);
    }
}