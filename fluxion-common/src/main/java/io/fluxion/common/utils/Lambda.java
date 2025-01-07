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

import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * todo 提升性能
 *
 * @author Devil
 */
@Slf4j
public class Lambda {

    private static final Pattern RETURN_TYPE_PATTERN = Pattern.compile("\\(.*\\)L(.*);");
    private static final Pattern PARAMETER_TYPE_PATTERN = Pattern.compile("\\((.*)\\).*");

    public static <T, R> String name(Func<T, R> func) {
        Class<?> clazz = func.getClass();
        try {
            Method method = clazz.getDeclaredMethod("writeReplace");
            method.setAccessible(true);
            SerializedLambda lambda = (SerializedLambda) method.invoke(func);
            return PropertyUtils.methodToProperty(lambda.getImplMethodName());
        } catch (Exception e) {
            throw new RuntimeException("name analyze fail");
        }
    }

    public static <T, R> Class<R> returnType(Func<T, R> func) {
        Class<?> clazz = func.getClass();
        try {
            Method method = clazz.getDeclaredMethod("writeReplace");
            method.setAccessible(true);
            SerializedLambda lambda = (SerializedLambda) method.invoke(func);
            return getReturnType(lambda);
        } catch (Exception e) {
            throw new RuntimeException("returnType analyze fail", e);
        }
    }

    private static <R> Class<R> getReturnType(SerializedLambda serializedLambda) throws Exception {
        String expr = serializedLambda.getInstantiatedMethodType();
        // 反序列化得到Method对象
        Matcher matcher = RETURN_TYPE_PATTERN.matcher(expr);
        if (!matcher.find() || matcher.groupCount() != 1) {
            throw new RuntimeException("获取Lambda信息失败");
        }
        String className = matcher.group(1).replace("/", ".");
        return (Class<R>) Class.forName(className);
    }

    @FunctionalInterface
    public interface Func<T, R> extends Function<T, R>, Serializable {
    }

}
