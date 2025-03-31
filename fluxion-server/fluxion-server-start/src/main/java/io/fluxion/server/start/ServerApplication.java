/*
 * Copyright 2025-2030 Fluxion Team (https://github.com/Fluxion-io).
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

package io.fluxion.server.start;

import org.axonframework.springboot.autoconfig.TransactionAutoConfiguration;
import org.axonframework.springboot.autoconfig.legacyjpa.JpaJavaxAutoConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @author Devil
 * @since 2021/7/24
 */
@SpringBootApplication(exclude = {
    JpaJavaxAutoConfiguration.class, // 目前不需要这些功能
    TransactionAutoConfiguration.class, // 给Cmd和Query都套了事务
})
@ComponentScan(basePackages = "io.fluxion")
@EnableTransactionManagement
@EntityScan(basePackages = "io.fluxion.**.dao.entity")
@EnableJpaRepositories(value = {"io.fluxion.**.dao.repository"})
public class ServerApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder()
            .web(WebApplicationType.SERVLET)
            .sources(ServerApplication.class)
            .build()
            .run(args);
    }

}
