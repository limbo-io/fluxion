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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for MySQL integration tests.
 * 
 * Supports two modes:
 * 1. Testcontainers (default): Automatically spins up MySQL 8 container
 * 2. External MySQL: Use environment variables when Docker is not available
 * 
 * Environment variables for external MySQL:
 * - FLUXION_TEST_MYSQL_HOST (default: localhost)
 * - FLUXION_TEST_MYSQL_PORT (default: 3306)
 * - FLUXION_TEST_MYSQL_DATABASE (default: fluxion_test)
 * - FLUXION_TEST_MYSQL_USERNAME (default: root)
 * - FLUXION_TEST_MYSQL_PASSWORD (default: empty)
 * 
 * Set FLUXION_TEST_MYSQL_URL to use a complete JDBC URL instead.
 */
@Testcontainers
public abstract class AbstractMySqlIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractMySqlIntegrationTest.class);

    private static final String ENV_MYSQL_URL = System.getenv("FLUXION_TEST_MYSQL_URL");
    private static final String ENV_MYSQL_HOST = System.getenv().getOrDefault("FLUXION_TEST_MYSQL_HOST", "localhost");
    private static final String ENV_MYSQL_PORT = System.getenv().getOrDefault("FLUXION_TEST_MYSQL_PORT", "3306");
    private static final String ENV_MYSQL_DATABASE = System.getenv().getOrDefault("FLUXION_TEST_MYSQL_DATABASE", "fluxion_test");
    private static final String ENV_MYSQL_USERNAME = System.getenv().getOrDefault("FLUXION_TEST_MYSQL_USERNAME", "root");
    private static final String ENV_MYSQL_PASSWORD = System.getenv().getOrDefault("FLUXION_TEST_MYSQL_PASSWORD", "");

    /**
     * Check if external MySQL is configured via environment variables.
     */
    private static final boolean USE_EXTERNAL_MYSQL = System.getenv("FLUXION_TEST_MYSQL_URL") != null ||
            System.getenv("FLUXION_TEST_MYSQL_HOST") != null;

    /**
     * MySQL 8 container for testing.
     * Only started when external MySQL is not configured.
     */
    @Container
    @SuppressWarnings("resource")
    static MySQLContainer<?> mysqlContainer = USE_EXTERNAL_MYSQL ? null :
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("fluxion_test")
                    .withUsername("root")
                    .withPassword("test")
                    .withCommand("--default-time-zone=+00:00 --explicit_defaults_for_timestamp=ON")
                    .withInitScript("schema-mysql.sql")
                    .withReuse(true);

    @DynamicPropertySource
    static void configureMySql(DynamicPropertyRegistry registry) {
        if (USE_EXTERNAL_MYSQL) {
            log.info("Using external MySQL via environment variables");
            String jdbcUrl = ENV_MYSQL_URL != null ? ENV_MYSQL_URL :
                String.format("jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                    ENV_MYSQL_HOST, ENV_MYSQL_PORT, ENV_MYSQL_DATABASE);
            
            registry.add("spring.datasource.url", () -> jdbcUrl);
            registry.add("spring.datasource.username", () -> ENV_MYSQL_USERNAME);
            registry.add("spring.datasource.password", () -> ENV_MYSQL_PASSWORD);
        } else {
            log.info("Using Testcontainers MySQL");
            registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
            registry.add("spring.datasource.username", mysqlContainer::getUsername);
            registry.add("spring.datasource.password", mysqlContainer::getPassword);
        }
        
        // Common datasource configuration for MySQL
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQL8Dialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }
}
