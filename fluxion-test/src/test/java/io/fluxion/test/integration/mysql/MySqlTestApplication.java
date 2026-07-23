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
import io.fluxion.server.infrastructure.lock.DistributedLock;
import io.fluxion.server.infrastructure.schedule.scheduler.DelayedTaskScheduler;
import io.fluxion.server.infrastructure.schedule.scheduler.Timer;
import io.fluxion.server.infrastructure.schedule.scheduler.TimingWheelTimer;
import io.fluxion.test.support.base.TestApplication;
import io.fluxion.test.support.environment.LocalDistributedLock;
import io.limbo.cqrs.spring.config.EnableCqrs;
import io.limbo.utils.ReflectionUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.concurrent.TimeUnit;

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
@ComponentScan(
    basePackages = "io.fluxion",
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = TestApplication.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = DatabaseDistributedLock.class)
    }
)
@EnableTransactionManagement
@EntityScan(basePackages = "io.fluxion.**.dao.entity")
@EnableJpaRepositories(value = {"io.fluxion.**.dao.repository"})
@EnableCqrs(basePackages = {"io.fluxion.server", "io.fluxion.worker"})
public class MySqlTestApplication {

    static {
        // Configure ReflectionUtils for test class loader
        ReflectionUtils.configure("io.fluxion");
    }

    public static void main(String[] args) {
        SpringApplication.run(MySqlTestApplication.class, args);
    }

    /**
     * H2 回归不模拟 MySQL 的原子 upsert 锁语义；该语义由生产 MySQL 实现负责。
     */
    @Bean
    @Primary
    public DistributedLock distributedLock() {
        return new LocalDistributedLock();
    }

    /**
     * cqrs-spring 0.0.3 按 handler 的全限定类名查找 Bean；为 Spring 默认命名
     * 的组件补充同名别名，避免扫描器反射创建未注入依赖的 handler 实例。
     */
    @Bean
    public static BeanFactoryPostProcessor cqrsHandlerBeanAliases() {
        return beanFactory -> {
            BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
            for (String beanName : beanFactory.getBeanDefinitionNames()) {
                String beanClassName = beanFactory.getBeanDefinition(beanName).getBeanClassName();
                if (beanClassName != null
                    && beanClassName.startsWith("io.fluxion.")
                    && !beanName.equals(beanClassName)
                    && !registry.isAlias(beanClassName)) {
                    registry.registerAlias(beanName, beanClassName);
                }
            }
        };
    }

    @Bean
    public Timer timer() {
        return new TimingWheelTimer(100, TimeUnit.MILLISECONDS);
    }

    @Bean(name = {"delayedTaskScheduler", "scheduler"})
    public DelayedTaskScheduler delayedTaskScheduler(Timer timer) {
        return new DelayedTaskScheduler(timer);
    }
}
