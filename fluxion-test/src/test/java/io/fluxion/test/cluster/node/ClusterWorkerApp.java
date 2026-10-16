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

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Worker 节点 Spring 上下文源。
 *
 * <p>刻意只带 {@code @Configuration}（无 @SpringBootApplication / @EnableAutoConfiguration）：
 * 本类会被集成测试的 {@code @ComponentScan("io.fluxion")} 捕获，
 * 若在此声明 autoconfigure exclude 会把 DataSource 排除项泄漏进那些上下文
 *（实测 H2 集成套件 42 项全部因缺 entityManagerFactory 初始化失败），
 * 声明 @EnableAutoConfiguration 同理不能出现在可被扫描的类上。
 * 节点装配由 ClusterWorkerBootstrap 显式 sources 驱动：
 * FluxionWorkerAutoConfiguration + 本类的 TestExecutor 扫描；
 * 全程无任何自动配置，Worker 本地任务仓储由
 * fluxion.worker.datasource.* 配置驱动（H2 按节点隔离）。</p>
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan("io.fluxion.test.cluster.node")
public class ClusterWorkerApp {
}