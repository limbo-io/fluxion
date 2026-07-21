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

package io.fluxion.test.integration.mysql;

import io.fluxion.server.infrastructure.lock.DatabaseDistributedLock;
import io.limbo.cqrs.spring.config.EnableCqrs;
import io.limbo.utils.ReflectionUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * MySQL Integration Test Application.
 * 
 * Unlike the regular TestApplication which uses in-memory LocalDistributedLock,
 * this application uses the real DatabaseDistributedLock to test MySQL-specific
 * behaviors like:
 * - INSERT ... ON DUPLICATE KEY UPDATE
 * - NOW(3) millisecond precision
 * - Row-level locking with SELECT FOR UPDATE
 * 
 * This allows testing actual distributed behaviors that H2 cannot properly simulate.
 */
@SpringBootApplication
@ComponentScan(basePackages = "io.fluxion")
@EnableTransactionManagement
@EntityScan(basePackages = "io.fluxion.**.dao.entity")
@EnableJpaRepositories(value = {"io.fluxion.**.dao.repository"})
@EnableCqrs(basePackages = "io.fluxion")
public class MySqlTestApplication {

    static {
        // Configure ReflectionUtils for test class loader
        ReflectionUtils.configure("io.fluxion");
    }

    public static void main(String[] args) {
        SpringApplication.run(MySqlTestApplication.class, args);
    }
}
