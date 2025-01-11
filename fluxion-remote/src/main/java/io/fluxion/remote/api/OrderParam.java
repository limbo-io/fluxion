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

package io.fluxion.remote.api;

/**
 * 排序
 * @author Devil
 * @date 2021/6/10 3:10 下午
 */
public class OrderParam {
    /**
     * 需要进行排序的字段
     */
    private String column;

    /**
     * 是否正序排列，默认 true
     */
    private String sort;

    public OrderParam(String column) {
        this(column, "asc");
        this.column = column;
    }

    public OrderParam(String column, String sort) {
        this.column = column;
        this.sort = sort;
    }

    public String getColumn() {
        return column;
    }

    public String getSort() {
        return sort;
    }
}
