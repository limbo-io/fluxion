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

package io.fluxion.common.utils.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.Lists;
import io.fluxion.common.utils.time.Formatters;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * @author Devil
 */
public class JacksonUtils {

    public static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<Map<String, Object>>() {
    };

    public static final ObjectMapper MAPPER = defaultObjectMapper();

    public static final String DEFAULT_NONE_OBJECT = "{}";

    public static final String DEFAULT_NONE_ARRAY = "[]";

    /**
     * 生成新的{@link ObjectMapper}
     */
    public static ObjectMapper defaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // 注册JDK8的日期API处理模块
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        // 注册LocalDateTime的类型处理
        String dateTimePattern = Formatters.YMD_HMS_SSS;
        javaTimeModule.addSerializer(new LocalDateTimePatternSerializer(dateTimePattern));
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimePatternDeserializer(Lists.newArrayList(dateTimePattern, Formatters.YMD_HMS)));
//        javaTimeModule.addSerializer(LocalDateTime.class, new com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer(DateTimeFormatter.ofPattern(Formatters.YMD_HMS)));
//        javaTimeModule.addDeserializer(LocalDateTime.class, new com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(Formatters.YMD_HMS)));
//        javaTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer(DateTimeFormatter.ofPattern(Formatters.YMD_HMS)));
//        javaTimeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(DateTimeFormatter.ofPattern(Formatters.YMD_HMS)));
//        javaTimeModule.addSerializer(LocalTime.class, new LocalTimeSerializer(DateTimeFormatter.ofPattern(Formatters.YMD_HMS)));
//        javaTimeModule.addDeserializer(LocalTime.class, new LocalTimeDeserializer(DateTimeFormatter.ofPattern(Formatters.YMD_HMS)));

        //在反序列化时忽略在 json 中存在但 Java 对象不存在的属性
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        //在序列化时日期格式默认为 yyyy-MM-dd HH:mm:ss
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


    /**
     * 将对象转换为JSON字符串
     */
    public static <T> String toJSONString(T t, ObjectMapper mapper) {
        Objects.requireNonNull(t);
        try {
            return mapper.writeValueAsString(t);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Jackson serialization fail！type=" + t.getClass().getName(), e);
        }
    }

    /**
     * 将对象转换为JSON字符串
     */
    public static <T> String toJSONString(T t) {
        if (t == null) {
            return StringUtils.EMPTY;
        }
        try {
            return MAPPER.writeValueAsString(t);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Jackson serialization fail！type=" + t.getClass().getName(), e);
        }
    }

    /**
     * 将对象转换为JSON字符串
     */
    public static <T> String toJSONString(T t, String defaultValue) {
        if (t == null) {
            return defaultValue;
        }
        try {
            return MAPPER.writeValueAsString(t);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Jackson serialization fail！type=" + t.getClass().getName(), e);
        }
    }

    /**
     * 解析为指定类型
     */
    public static <T> T toType(String json, Type type) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        Objects.requireNonNull(type);
        try {
            JavaType javaType = TypeFactory.defaultInstance().constructType(type);
            return MAPPER.readValue(json, javaType);
        } catch (Exception e) {
            throw new IllegalStateException("Jackson deserialization fail！type:" + type + " json:" + json, e);
        }
    }

    /**
     * 解析为指定类型
     */
    public static <T> T toType(String json, TypeReference<T> type) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        Objects.requireNonNull(type);
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Jackson deserialization fail！type:" + type + "json:" + json, e);
        }
    }


}