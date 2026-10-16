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

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;

/**
 * Worker 节点 Spring 上下文。
 *
 * <p>仅扫描本包（测试执行器）。WebApplicationType.NONE 使 server 端自动配置
 * （@ConditionalOnWebApplication）自动失效，双 starter 共存于测试 jarclasspath 时不启动 Broker。
 * Worker 本地任务仓储由 fluxion.worker.datasource.* 配置驱动（H2 按节点隔离），
 * 无需 Spring DataSource，排除相关自动配置避免误建连接池。</p>
 */
@SpringBootApplication(
    scanBasePackages = "io.fluxion.test.cluster.node",
    exclude = {
        DataSourceAutoConfiguration.class,
        FlywayAutoConfiguration.class
    }
)
public class ClusterWorkerApp {
}