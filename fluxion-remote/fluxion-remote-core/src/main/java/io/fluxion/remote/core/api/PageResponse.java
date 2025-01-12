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


import java.util.Collections;
import java.util.List;

/**
 * @author Brozen
 * @since 2021-06-16
 */
public class PageResponse<T> extends Response<List<T>> {

    /**
     * 页码
     */
    private int current = 1;

    /**
     * 每页条数
     */
    private int pageSize = 20;

    /**
     * 总条数
     */
    private long total = 0;

    public PageResponse(int current, int pageSize, long total, List<T> data) {
        super(HttpStatus.OK, SUCCESS_MSG, data);
        this.current = current;
        this.pageSize = pageSize;
        this.total = total;
    }

    /**
     * 是否还有下一页
     */
    public Boolean getHasNext() {
        return total > current * pageSize;
    }

    /**
     * 获取分页查询的偏移条数
     */
    public int getOffset() {
        return pageSize * (current - 1);
    }

    @Override
    public List<T> getData() {
        return super.getData() == null ? Collections.emptyList() : super.getData();
    }

}
