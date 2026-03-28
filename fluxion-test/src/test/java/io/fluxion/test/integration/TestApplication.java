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

package io.fluxion.test.integration;

import io.fluxion.server.infrastructure.lock.DatabaseDistributedLock;
import io.fluxion.server.infrastructure.lock.DistributedLock;
import io.limbo.cqrs.spring.config.EnableCqrs;
import io.limbo.utils.ReflectionUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 集成测试应用入口
 *
 * @author Devil
 */
@SpringBootApplication
@ComponentScan(
    basePackages = "io.fluxion"
)
@EnableTransactionManagement
@EntityScan(basePackages = "io.fluxion.**.dao.entity")
@EnableJpaRepositories(value = {"io.fluxion.**.dao.repository"})
public class TestApplication {

    static {
        // 提前配置 ReflectionUtils，确保在测试类加载器中正确扫描
        ReflectionUtils.configure("io.fluxion");
    }

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }

    @Configuration
    @EnableCqrs(basePackages = {"io.fluxion"})
    static class TestConfig {
        // Configuration class that enables CQRS with explicit handlers

        /**
         * Provide DistributedLock bean for integration tests
         */
        @Bean
        public DistributedLock distributedLock() {
            return new DatabaseDistributedLock();
        }
    }
}
