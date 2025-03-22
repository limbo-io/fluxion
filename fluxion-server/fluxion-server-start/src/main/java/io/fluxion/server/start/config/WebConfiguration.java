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

package io.fluxion.server.start.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.common.utils.time.Formatters;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.lifecycle.Phase;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.interceptors.LoggingInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.datetime.DateFormatter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author Devil
 * @since 2021/7/26
 */
@Slf4j
@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    /**
     * json 返回结果处理
     */
    @Bean
    public ObjectMapper jacksonObjectMapper() {
        return JacksonUtils.MAPPER;
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addFormatter(new DateFormatter(Formatters.YMD_HMS));
    }

//    @Bean
//    public LoggingInterceptor<Message<?>> loggingInterceptor() {
//        return new LoggingInterceptor<>();
//    }
//
//    @Bean
//    public ConfigurerModule configurerModule(LoggingInterceptor<Message<?>> loggingInterceptor) {
//        return configurer -> configurer.eventProcessing(processingConfigurer ->
//                processingConfigurer.usingSubscribingEventProcessors()
//                    .registerDefaultHandlerInterceptor((config, processorName) -> loggingInterceptor)
//            )
//            .onInitialize(config -> config.onStart(Phase.INSTRUCTION_COMPONENTS + 1, () -> {
//                config.commandBus().registerHandlerInterceptor(loggingInterceptor);
//                config.commandBus().registerDispatchInterceptor(loggingInterceptor);
//                config.eventBus().registerDispatchInterceptor(loggingInterceptor);
//                config.queryBus().registerHandlerInterceptor(loggingInterceptor);
//                config.queryBus().registerDispatchInterceptor(loggingInterceptor);
//                config.queryUpdateEmitter().registerDispatchInterceptor(loggingInterceptor);
//            }));
//    }

}
