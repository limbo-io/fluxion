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

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import io.fluxion.common.utils.ReflectionsUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Set;

public class JacksonTypeIdResolver implements TypeIdResolver {
    private JavaType baseType;

    @Override
    public void init(JavaType javaType) {
        baseType = javaType;
    }


    @Override
    public String idFromValue(Object o) {
        return idFromValueAndType(o, o.getClass());
    }


    @Override
    public String idFromValueAndType(Object o, Class<?> aClass) {
        //有出现同名类时可以用这个来做区别
        JsonTypeName annotation = aClass.getAnnotation(JsonTypeName.class);
        if (annotation != null) {
            return annotation.value();
        }
        String name = aClass.getName();
        String[] splits = StringUtils.split(name, ".");
        return splits[splits.length - 1];
    }


    @Override
    public JavaType typeFromId(DatabindContext databindContext, String type) {
        Class<?> clazz = getSubType(type);
        if (clazz == null) {
            throw new IllegalStateException("cannot find class '" + type + "'");
        }
        return databindContext.constructSpecializedType(baseType, clazz);
    }

    public Class<?> getSubType(String type) {
        Set<Class<?>> subTypes = ReflectionsUtils.get().getSubTypesOf((Class<Object>) baseType.getRawClass());
        for (Class<?> subType : subTypes) {
            JsonTypeName annotation = subType.getAnnotation(JsonTypeName.class);
            if ((annotation != null && annotation.value().equals(type))
                || (subType.getSimpleName().equals(type))
                || subType.getName().equals(type)) {
                return subType;
            }
        }
        return null;
    }

    @Override
    public String idFromBaseType() {
        return idFromValueAndType(null, baseType.getClass());
    }

    @Override
    public String getDescForKnownTypeIds() {
        return null;
    }

    @Override
    public JsonTypeInfo.Id getMechanism() {
        return JsonTypeInfo.Id.CUSTOM;
    }

}