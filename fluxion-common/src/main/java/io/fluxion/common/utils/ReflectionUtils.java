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

package io.fluxion.common.utils;

import org.reflections.Reflections;

import java.lang.reflect.ParameterizedType;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Devil
 */
public class ReflectionUtils {

    private final static Reflections REFLECTIONS = new Reflections();

    /**
     * T<R> cache T -> R
     */
    private static final Map<Class<?>, Class<?>> TYPE_CACHE = new ConcurrentHashMap<>();

    /**
     * 获取某个类的子类型
     */
    public static <T> Set<Class<? extends T>> subTypesOf(Class<T> type) {
        return REFLECTIONS.getSubTypesOf(type);
    }

    /**
     * 获取某个类的泛型类型
     */
    public static <R, Q> Class<R> refType(Q obj) {
        return (Class<R>) TYPE_CACHE.computeIfAbsent(
            obj.getClass(),
            clazz -> (Class<R>) ((ParameterizedType) clazz.getGenericInterfaces()[0]).getActualTypeArguments()[0]
        );
    }


}
