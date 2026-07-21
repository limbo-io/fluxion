/*
 * Copyright 2025-2030 Limbo Team (https://github.com/limbo-io).
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

package io.fluxion.test.support.base;

import io.fluxion.server.infrastructure.lock.DatabaseDistributedLock;
import io.fluxion.server.infrastructure.lock.DistributedLock;
import io.fluxion.test.support.environment.LocalDistributedLock;
import io.limbo.cqrs.spring.config.EnableCqrs;
import io.limbo.utils.ReflectionUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 【集成测试应用入口】
 * 
 * 作用：Spring Boot 测试应用的启动类
 *       配置嵌入式测试环境所需的组件和扫描路径
 * 
 * 与生产环境的不同：
 *   1. 使用 LocalDistributedLock 替换 DatabaseDistributedLock
 *      - 避免依赖数据库锁表
 *      - 单 JVM 内存锁足够测试使用
 *   
 *   2. 扫描测试专用的 Executor（SimpleTestExecutor 等）
 *      - 位于 io.fluxion.test.support.executors
 *   
 *   3. 使用 H2 内存数据库（通过 profile 配置）
 *      - 自动建表、无需外部 MySQL
 * 
 * 加载的组件：
 *   - Broker 核心服务（扫描 io.fluxion.server）
 *   - Worker 核心服务（扫描 io.fluxion.worker）
 *   - 测试专用执行器（SimpleTestExecutor, CounterExecutor, FailingExecutor）
 *   - CQRS Command/Query Handlers（自动扫描）
 *   - H2 数据库 + Flyway 迁移
 * 
 * 使用方式：
 *   继承 {@link BaseIntegrationTest} 自动使用此配置
 *   或通过 @SpringBootTest(classes = TestApplication.class) 指定
 * 
 * @author Devil
 * @see BaseIntegrationTest
 */
@SpringBootApplication
@ComponentScan(
    basePackages = "io.fluxion",
    excludeFilters = {
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            // 排除生产环境的数据库分布式锁实现
            // 使用 LocalDistributedLock 替代
            classes = DatabaseDistributedLock.class
        ),
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "io\\.fluxion\\.test\\.integration\\.mysql\\..*"
        )
    }
)
@EnableTransactionManagement
@EntityScan(basePackages = "io.fluxion.**.dao.entity")
@EnableJpaRepositories(value = {"io.fluxion.**.dao.repository"})
// 启用 CQRS，自动扫描 Command/Query Handler
@EnableCqrs(basePackages = "io.fluxion")
public class TestApplication {

    static {
        // ===== ReflectionUtils 预配置 =====
        // 提前配置工具类，确保在测试类加载器中正确扫描
        // 避免类加载顺序问题导致的扫描失败
        ReflectionUtils.configure("io.fluxion");
    }

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }

    /**
     * 【测试专用】本地分布式锁
     * 
     * 替换生产环境的 DatabaseDistributedLock
     * 
     * 原因：
     *   - 测试环境为单 JVM，不需要跨进程互斥
     *   - 避免依赖数据库锁表，简化测试配置
     *   - 内存锁性能更好，无网络开销
     * 
     * @Primary: 优先于被排除的 DatabaseDistributedLock
     */
    @Bean
    @Primary
    public DistributedLock distributedLock() {
        return new LocalDistributedLock();
    }
}
