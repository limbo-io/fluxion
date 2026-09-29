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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.limbo.utils.time.Formatters;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.datetime.DateFormatter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.text.SimpleDateFormat;

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
        ObjectMapper mapper = new ObjectMapper();

        // 注册JDK8的日期API处理模块
        JavaTimeModule javaTimeModule = new JavaTimeModule();

        //在反序列化时忽略在 json 中存在但 Java 对象不存在的属性
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        // 管理/API 的运行态时间返回 RFC 3339 UTC 字符串（Instant → 2026-09-28T12:00:00Z，
        // 见 CONTEXT.md「统一时间模型」：HTTP 由 JavaTimeModule 默认 ISO 输出承载；
        // Worker RPC 不走本 mapper，其线协议为 epoch millis（DTO 字段为 Long））。
        // 不关 WRITE_DATES_AS_TIMESTAMPS 时 Jackson 会把 Instant 写成十进制秒，不是 millis。
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        //在序列化时日期格式默认为 yyyy-MM-dd HH:mm:ss
        String dateTimePattern = Formatters.YMD_HMS;
        mapper.setDateFormat(new SimpleDateFormat(dateTimePattern));
        mapper.getDeserializationConfig().with(new SimpleDateFormat(dateTimePattern));

        //在序列化时忽略值为 null 的属性
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        mapper.registerModules(
                javaTimeModule,
                new Jdk8Module()
        );
        return mapper;
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addFormatter(new DateFormatter(Formatters.YMD_HMS));
    }

}
