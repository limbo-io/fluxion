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

package io.fluxion.remote.core.api;

import org.apache.commons.collections4.CollectionUtils;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Positive;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Brozen
 * @date 2020/3/6 8:35 AM
 * @email brozen@qq.com
 */
public class PageRequest {
    /**
     * 页码，从1开始
     */
    @Positive(message = "页码不可为负数")
    @Min(value = 1, message = "页码从1开始")
    private int current = 1;

    /**
     * 每页条数
     */
    @Positive(message = "条数不可为负数")
    @Max(value = 1000, message = "每页最多1000条数据")
    private int pageSize = 20;

    /**
     * 最大条数
     */
    public static final int MAX_SIZE = 1000;

    /**
     * 排序字段 和排序方式保持统一长度
     */
    private List<String> orderBy;

    /**
     * 排序方式, 和排序字段保持统一长度
     */
    private List<String> sort;

    /**
     * 是否查询总数
     */
    private boolean searchCount = true;

    /**
     * 获取分页查询的偏移条数
     */
    public int getOffset() {
        return pageSize * (current - 1);
    }

    public int getCurrent() {
        return current;
    }

    public int getPageSize() {
        return pageSize;
    }

    public List<String> getOrderBy() {
        return orderBy;
    }

    public List<String> getSort() {
        return sort;
    }

    public boolean isSearchCount() {
        return searchCount;
    }

    public List<OrderParam> getOrders() {
        List<OrderParam> orders = new ArrayList<>();
        if (CollectionUtils.isEmpty(orderBy)) {
            return orders;
        }
        for (int i = 0; i < orderBy.size(); i++) {
            if (CollectionUtils.isNotEmpty(sort) && i < sort.size()) {
                orders.add(new OrderParam(orderBy.get(i), sort.get(i)));
            } else {
                orders.add(new OrderParam(orderBy.get(i)));
            }
        }
        return orders;
    }

    public <T> PageResponse<T> response(long total, List<T> data) {
        return new PageResponse<>(current, pageSize, total, data);
    }

    public <T> PageResponse<T> empty() {
        return response(0, Collections.emptyList());
    }

}
