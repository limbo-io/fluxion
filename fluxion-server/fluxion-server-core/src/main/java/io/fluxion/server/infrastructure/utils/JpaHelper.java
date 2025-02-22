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

package io.fluxion.server.infrastructure.utils;

import io.fluxion.common.utils.Lambda;
import io.fluxion.remote.core.api.PageRequest;
import org.springframework.data.domain.Pageable;

import javax.persistence.criteria.*;

/**
 * @author KaiFengCai
 * @since 2023/1/30
 */
public class JpaHelper {

    /**
     * PageParam 转换为 Pageable
     */
    public static Pageable pageable(PageRequest page) {
        // PageParam 从 1 开始 Pageable 从 0 开始
        return org.springframework.data.domain.PageRequest.of(page.getCurrent() - 1, page.getPageSize());
    }

    public static Pageable pageable(int page, int size) {
        // PageParam 从 1 开始 Pageable 从 0 开始
        return org.springframework.data.domain.PageRequest.of(page, size);
    }

    private static <T, R> Expression<R> expression(Root<T> root, Lambda.Func<T, R> func) {
        String fieldName = Lambda.name(func);
        Class<R> clazz = Lambda.returnType(func);
        return root.get(fieldName).as(clazz);
    }

    public static <T, R> Predicate equal(Root<T> root, CriteriaBuilder cb, Lambda.Func<T, R> func, R value) {
        return cb.equal(JpaHelper.expression(root, func), value);
    }

    public static <T> Predicate like(Root<T> root, CriteriaBuilder cb, Lambda.Func<T, String> func, String value) {
        return cb.like(JpaHelper.expression(root, func), value);
    }

    public static <T, R> Order desc(Root<T> root, CriteriaBuilder cb, Lambda.Func<T, R> func) {
        return cb.desc(JpaHelper.expression(root, func));
    }

}
